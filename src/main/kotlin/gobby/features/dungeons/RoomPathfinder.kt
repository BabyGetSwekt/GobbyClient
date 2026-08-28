package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpPathfinder
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.gui.map.InteractiveMapScreen
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpExecutionMode
import gobby.pathfinder.etherwarp.EtherwarpExecutionSettings
import gobby.pathfinder.etherwarp.EtherwarpPathExecutor
import gobby.pathfinder.etherwarp.DungeonPathPlanningExecutor
import gobby.pathfinder.etherwarp.DungeonRoomRoutePreparation
import gobby.pathfinder.navigation.PreparedDungeonRouteCache
import gobby.pathfinder.navigation.RoomGraphCache
import gobby.pathfinder.navigation.PreparedSegmentCache
import gobby.pathfinder.navigation.SnapshotLandingCandidateCache
import gobby.pathfinder.navigation.AtlasRoomPreparationCache
import gobby.pathfinder.navigation.AtlasRoomQueryCache
import gobby.pathfinder.world.BlockCache
import gobby.utils.rotation.ServerRotationLeaseManager
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.executeLater
import gobby.utils.render.BlockRenderUtils
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

object RoomPathfinder : Module("Room Pathfinder", "Opens an interactive map when the keybind is pressed. It will pathfind into the selected dungeon room if possible", Category.DUNGEONS) {

    private val openKey = KeybindSetting("Open Map", desc = "Opens the interactive map on screen").also { settings.add(it) }
    val pathDebug by BooleanSetting("Path Debug", false, desc = "Logs per teleport where it landed + whether it was server-side sneaked (no delay)")
    private val zeroPing by BooleanSetting("Zero Ping", false, desc = "Sends rapid Etherwarp casts without waiting for teleport acknowledgement")
    private val rotateInstead by BooleanSetting("Rotate Instead", false, desc = "Rotates the client view during rapid casts").withDependency { zeroPing }
    private val serverRotate by BooleanSetting("Server Rotate", false, desc = "Sends rapid casts through the server rotation lease").withDependency { zeroPing && !rotateInstead }
    private val rotateWaitServerTick by BooleanSetting("Wait For Server Tick", true, desc = "Waits for a fresh server tick before rotation-mode casts").withDependency { zeroPing && (rotateInstead || serverRotate) }
    private val rotationVariance by BooleanSetting("Rotation Variance", false, desc = "Uses safe per-hop aim variance during execution")
    private val teleportSmoothing by BooleanSetting("Teleport Smoothing", false, desc = "Smooths the visual transition after authoritative teleports").withDependency { zeroPing }
    private val keepLastServerRotation by BooleanSetting("Keep Last Server Rotation", false, desc = "Keeps the final server rotation after a server-rotate route").withDependency { serverRotate }
    private var keyWasDown = false

    @Volatile
    var pathPreview: List<EtherwarpNode> = emptyList()

    @Volatile
    var missedNode: Vec3? = null

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        pollOpenKey()
        updateExecutionSettings()
        BlockCache.flushDirtyLoadedChunks()
        EtherwarpPathExecutor.tick()
    }

    private fun updateExecutionSettings() {
        EtherwarpPathExecutor.configuredMode = when {
            !zeroPing -> EtherwarpExecutionMode.AWAIT_TELEPORT
            rotateInstead -> EtherwarpExecutionMode.ROTATE
            serverRotate -> EtherwarpExecutionMode.SERVER_ROTATE
            else -> EtherwarpExecutionMode.PACKET
        }
        EtherwarpExecutionSettings.rotationVarianceEnabled = rotationVariance
        EtherwarpExecutionSettings.teleportSmoothingEnabled = teleportSmoothing
        EtherwarpExecutionSettings.keepLastServerRotationEnabled = keepLastServerRotation
        EtherwarpExecutionSettings.rotateWaitServerTickEnabled = rotateWaitServerTick
        EtherwarpExecutionSettings.setRapidCastSpacingMillis(0)
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity) return
        if (!enabled || !pathDebug) return
        val path = pathPreview
        path.zipWithNext { from, to ->
            BlockRenderUtils.drawLine3D(event.matrixStack, event.camera, from.eye, to.eye, PATH_LINE_COLOR)
        }
        path.forEach { node ->
            val box = AABB(node.x - NODE_HALF, node.y - NODE_HALF, node.z - NODE_HALF, node.x + NODE_HALF, node.y + NODE_HALF, node.z + NODE_HALF)
            BlockRenderUtils.draw3DBox(event.matrixStack, event.camera, box, PATH_NODE_COLOR, filled = true)
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
        DungeonPathPlanningExecutor.cancel()
        DungeonRoomRoutePreparation.cancel()
        EtherwarpPathfinder.cancelPreload()
        EtherwarpPathExecutor.cancel()
        PreparedDungeonRouteCache.clear()
        RoomGraphCache.clear()
        PreparedSegmentCache.clear()
        SnapshotLandingCandidateCache.clear()
        AtlasRoomPreparationCache.clear()
        AtlasRoomQueryCache.clear()
        ServerRotationLeaseManager.reset()
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
