package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.render.Interpolate
import gobby.utils.render.RenderUtils.drawStringInWorld
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import kotlin.math.roundToInt

object RenderHealth : Module("Render Health", "Renders the current/max health on every living mob, scaled to the mob's size", Category.DEVELOPER) {

    private val LABEL_HP = Regex("""([\d,]+)\s*/\s*([\d,]+)❤""")

    private fun parseLabelMax(stand: ArmorStand): Int? {
        val name = stand.customName?.string ?: return null
        return LABEL_HP.find(name)?.groupValues?.get(2)?.replace(",", "")?.toIntOrNull()
    }

    private fun findLabelMax(entity: LivingEntity): Int? {
        val world = entity.level()
        val box = entity.boundingBox.inflate(0.5, 1.5, 0.5).move(0.0, 0.5, 0.0)
        val stand = world.getEntities(entity, box)
            .filterIsInstance<ArmorStand>()
            .firstOrNull() ?: return null
        return parseLabelMax(stand)
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity) return
        if (!enabled) return
        val world = mc.level ?: return
        val player = mc.player ?: return

        world.entitiesForRendering().asSequence()
            .filterIsInstance<LivingEntity>()
            .filter { it !is ArmorStand && it !== player && it.isAlive }
            .forEach { entity ->
                val max = findLabelMax(entity) ?: entity.maxHealth.roundToInt()
                val current = entity.health.roundToInt().coerceAtMost(max)
                val pos = Interpolate.interpolateEntity(entity).add(0.0, entity.boundingBox.ysize / 2.0, 0.0)
                drawStringInWorld("$current/$max§c❤", pos, event.matrixStack, event.camera, scale = 0.04f * entity.scale.coerceAtLeast(1f))
            }
    }
}
