package gobby.features.mining

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.Island
import gobby.utils.LocationUtils
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.render.Interpolate
import gobby.utils.render.RenderUtils.drawStringInWorld
import gobby.utils.skyblockID
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

private data class Corpse(val id: String, val helmet: String, val label: String, val color: Color)

object CorpseEsp : Module("Corpse ESP", "Highlights unlooted corpses in Glacite Mineshafts", Category.MINING) {

    private val showDistance by BooleanSetting("Show Distance", true, desc = "Adds the distance to the corpse label")
    private val espLine by BooleanSetting("ESP Line", false, desc = "Draws a tracer from your crosshair to each corpse")

    private val CORPSES = listOf(
        Corpse("LAPIS_ARMOR_HELMET", "Lapis Armor Helmet", "Lapis", Color(60, 90, 255)),
        Corpse("MINERAL_HELMET", "Mineral Helmet", "Tungsten", Color(235, 235, 235)),
        Corpse("ARMOR_OF_YOG_HELMET", "Yog Helmet", "Umber", Color(181, 98, 34)),
        Corpse("VANGUARD_HELMET", "Vanguard Helmet", "Vanguard", Color(242, 36, 184))
    )

    private val claimed = mutableListOf<Vec3>()

    private val inCorpseWorld: Boolean
        get() = LocationUtils.isIn(Island.MINESHAFT)

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !enabled) return
        val lineStart = if (espLine) Interpolate.interpolatedLookVec() else null
        corpses().forEach { (stand, corpse) ->
            val center = Interpolate.interpolateEntity(stand).add(0.0, 1.0, 0.0)
            draw3DBox(event.matrixStack, event.camera, AABB.ofSize(center, 1.0, 1.0, 1.0), corpse.color, filled = false)
            drawStringInWorld(labelFor(stand, corpse), center.add(0.0, 1.2, 0.0), event.matrixStack, event.camera, corpse.color, 0.025f, 0.006f)
            lineStart?.let { drawLine3D(event.matrixStack, event.camera, it, center, corpse.color) }
        }
    }

    private fun labelFor(stand: ArmorStand, corpse: Corpse): String {
        if (!showDistance) return corpse.label
        val distance = mc.player?.distanceTo(stand)?.toInt() ?: return corpse.label
        return "${corpse.label} ${distance}m"
    }

    private fun corpses(): List<Pair<ArmorStand, Corpse>> =
        mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>()
            ?.mapNotNull { stand -> corpseFor(stand)?.let { stand to it } } ?: emptyList()

    private fun corpseFor(entity: Entity): Corpse? {
        if (!inCorpseWorld) return null
        val stand = entity as? ArmorStand ?: return null
        if (stand.customName != null || stand.isInvisible || stand.isClaimed) return null
        return corpseOf(stand.getItemBySlot(EquipmentSlot.HEAD))
    }

    private fun corpseOf(helmet: ItemStack): Corpse? {
        if (helmet.isEmpty) return null
        val name = helmet.hoverName.string.noControlCodes.trim()
        return CORPSES.firstOrNull { it.id == helmet.skyblockID || it.helmet == name }
    }

    private val ArmorStand.isClaimed: Boolean
        get() = claimed.any { it.distanceTo(position()) < 5.0 }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled) return
        val message = event.message.trim()
        val corpse = CORPSES.firstOrNull { message.startsWith("${it.label} CORPSE LOOT!", ignoreCase = true) } ?: return
        val player = mc.player?.position() ?: return
        val looted = corpses().filter { it.second == corpse }.minByOrNull { it.first.position().distanceTo(player) } ?: return
        claimed += looted.first.position()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) = claimed.clear()
}
