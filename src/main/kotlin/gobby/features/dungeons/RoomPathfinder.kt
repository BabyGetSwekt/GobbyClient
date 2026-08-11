package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.pathfinder.world.CacheDiagnostics
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.events.render.NewRender3DEvent
import gobby.gui.map.InteractiveMapScreen
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpPathExecutor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.executeLater
import gobby.utils.render.BlockRenderUtils
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

object RoomPathfinder : Module("Room Pathfinder", "Opens an interactive map when the keybind is pressed. It will pathfind into the selected dungeon room if possible", Category.DUNGEONS) {

    private val openKey = KeybindSetting("Open Map", desc = "Opens the interactive map on screen").also { settings.add(it) }
    val pathDebug by BooleanSetting("Path Debug", true, desc = "Logs per teleport where it landed + whether it was server-side sneaked (no delay)")
    private val cacheDebug by BooleanSetting("Cache Debug", false, desc = "Logs every block cache capture to console and disk, costs FPS")
    private var keyWasDown = false

    @Volatile
    var pathPreview: List<EtherwarpNode> = emptyList()

    @Volatile
    var missedNode: Vec3? = null

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        CacheDiagnostics.enabled = cacheDebug
        pollOpenKey()
        EtherwarpPathExecutor.tick()
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled) return
        val path = pathPreview
        if (path.size >= 2) {
            (0 until path.size - 1).forEach { i ->
                BlockRenderUtils.drawLine3D(event.matrixStack, event.camera, path[i].eye, path[i + 1].eye, PATH_LINE_COLOR)
            }
            path.forEach { node ->
                val box = AABB(node.x - NODE_HALF, node.y - NODE_HALF, node.z - NODE_HALF, node.x + NODE_HALF, node.y + NODE_HALF, node.z + NODE_HALF)
                BlockRenderUtils.draw3DBox(event.matrixStack, event.camera, box, PATH_NODE_COLOR, filled = true)
            }
        }
        missedNode?.let { n ->
            val box = AABB(n.x - MISS_HALF, n.y - MISS_HALF, n.z - MISS_HALF, n.x + MISS_HALF, n.y + MISS_HALF, n.z + MISS_HALF)
            BlockRenderUtils.draw3DBox(event.matrixStack, event.camera, box, MISS_NODE_COLOR, filled = true)
        }
    }

    private fun pollOpenKey() {
        val down = enabled && inDungeons && !inBoss && mc.gui.screen() == null && openKey.isPressed()
        if (down && !keyWasDown) mc.executeLater { mc.gui.setScreen(InteractiveMapScreen()) }
        keyWasDown = down
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        EtherwarpPathExecutor.cancel()
        pathPreview = emptyList()
        missedNode = null
        keyWasDown = false
    }

    private const val NODE_HALF = 0.2
    private const val MISS_HALF = 0.35
    private val PATH_LINE_COLOR = Color(0, 255, 255)
    private val PATH_NODE_COLOR = Color(255, 255, 0, 160)
    private val MISS_NODE_COLOR = Color(255, 0, 0, 180)
}
