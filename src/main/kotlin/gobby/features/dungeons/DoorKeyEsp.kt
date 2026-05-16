package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.Interpolate
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import java.awt.Color
import kotlin.math.abs

object DoorKeyEsp : Module("Door Key ESP", "Highlights a box around wither keys and blood keys", Category.DUNGEONS) {

    private val witherColor = Color(23, 19, 23, 91)
    private val bloodColor = Color(250, 0, 0, 51)

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled || !inDungeons) return
        val stands = mc.level?.entitiesForRendering()?.filterIsInstance<ArmorStand>() ?: return
        stands.forEach { label ->
            val color = colorFor(label.customName?.string) ?: return@forEach
            val keyStand = stands.findKeyStand(label) ?: label
            val center = Interpolate.interpolateEntity(keyStand).add(0.0, 1.75, 0.0)
            draw3DBox(event.matrixStack, event.camera, AABB.ofSize(center, 1.0, 1.0, 1.0), color)
        }
    }

    private fun colorFor(name: String?): Color? = when {
        name == null -> null
        "Wither Key" in name -> witherColor
        "Blood Key" in name -> bloodColor
        else -> null
    }

    private fun List<ArmorStand>.findKeyStand(label: ArmorStand) = firstOrNull { e ->
        e !== label
            && abs(e.x - label.x) < 0.05
            && abs(e.z - label.z) < 0.05
            && e.y < label.y && label.y - e.y < 1.0
            && e.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.PLAYER_HEAD)
    }
}
