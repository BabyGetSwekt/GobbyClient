package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.render.Interpolate
import gobby.utils.render.RenderUtils.drawStringInWorld
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import kotlin.math.roundToInt

object RenderHealth : Module("Render Health", "Renders the current/max health on every living mob, scaled to the mob's size", Category.DEVELOPER) {

    private val LABEL_HP = Regex("""([\d,]+)\s*/\s*([\d,]+)❤""")

    private fun parseLabelMax(stand: ArmorStandEntity): Int? {
        val name = stand.customName?.string ?: return null
        return LABEL_HP.find(name)?.groupValues?.get(2)?.replace(",", "")?.toIntOrNull()
    }

    private fun findLabelMax(entity: LivingEntity): Int? {
        val world = entity.entityWorld
        val box = entity.boundingBox.expand(0.5, 1.5, 0.5).offset(0.0, 0.5, 0.0)
        val stand = world.getOtherEntities(entity, box) { it is ArmorStandEntity && it.customName != null }
            .filterIsInstance<ArmorStandEntity>()
            .firstOrNull() ?: return null
        return parseLabelMax(stand)
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled) return
        val world = mc.world ?: return
        val player = mc.player ?: return

        world.entities.asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it !is ArmorStandEntity && it !== player && it.isAlive }
            .forEach { entity ->
                val max = findLabelMax(entity) ?: entity.maxHealth.roundToInt()
                val current = entity.health.roundToInt().coerceAtMost(max)
                val pos = Interpolate.interpolateEntity(entity).add(0.0, entity.boundingBox.lengthY / 2.0, 0.0)
                drawStringInWorld("$current/$max§c❤", pos, event.matrixStack, event.camera, scale = 0.04f * entity.scale.coerceAtLeast(1f))
            }
    }
}
