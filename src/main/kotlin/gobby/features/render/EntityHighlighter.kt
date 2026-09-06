package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.render.Interpolate
import gobby.utils.render.Render3D.drawEntityModel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import java.awt.Color

private const val LINE_MODE_FEET = 0
private const val LINE_MODE_CROSSHAIR = 1

enum class EspStyle { MODEL, BOX, FILLED_BOX, CONNECTED }

abstract class EntityHighlighter(
    name: String,
    description: String = "",
    category: Category,
    defaultEnabled: Boolean = false
) : Module(name, description, category, toggled = true, defaultEnabled = defaultEnabled) {

    private val cachedMobs = mutableMapOf<Entity, Entity>()

    @SubscribeEvent
    fun onRender(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !enabled) return
        val context = event.context
        val poseStack = context.poseStack()
        val collector = context.submitNodeCollector()
        val camera = mc.gameRenderer.mainCamera()
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val lineColor = getLineColor()
        val player = mc.player
        val lineStart = if (shouldDrawLines() && player != null) {
            if (getLineMode() == LINE_MODE_FEET) Interpolate.interpolateEntity(player) else Interpolate.interpolatedLookVec()
        } else null

        forEachHighlight { renderEntity, sourceEntity ->
            val color = getColorFor(sourceEntity)
            when (espStyle()) {
                EspStyle.MODEL -> drawEntityModel(poseStack, collector, camera, delta, renderEntity, color, rendersArmor())
                EspStyle.BOX -> draw3DBox(poseStack, camera, boxFor(renderEntity), color, filled = false)
                EspStyle.FILLED_BOX -> draw3DBox(poseStack, camera, boxFor(renderEntity), color, filled = true)
                EspStyle.CONNECTED -> {
                    val box = boxFor(renderEntity)
                    draw3DBox(poseStack, camera, box, color, filled = true)
                    draw3DBox(poseStack, camera, box, Color(color.red, color.green, color.blue), filled = false)
                }
            }
            lineStart?.let { drawLine3D(poseStack, camera, it, Interpolate.interpolateEntity(renderEntity), lineColor) }
        }
    }

    open fun espStyle(): EspStyle = EspStyle.MODEL

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!usesMobCaching() || !enabled) return
        val world = mc.level ?: return

        for (entity in world.entitiesForRendering()) {
            if (!shouldHighlight(entity)) continue
            val mob = getCorrespondingMob(entity) ?: continue
            if (!shouldCacheMob(mob)) continue
            cachedMobs[mob] = entity
        }

        cachedMobs.entries.removeIf { !it.key.isAlive || (revalidateCache() && !shouldHighlight(it.value)) }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        cachedMobs.clear()
    }

    private inline fun forEachHighlight(action: (render: Entity, source: Entity) -> Unit) {
        if (usesMobCaching()) {
            cachedMobs.forEach { (mob, source) -> if (mob.isAlive) action(mob, source) }
        } else {
            val world = mc.level ?: return
            world.entitiesForRendering().forEach { entity ->
                if (!shouldHighlight(entity)) return@forEach
                action(resolveEntity(entity) ?: return@forEach, entity)
            }
        }
    }

    protected open fun boxFor(entity: Entity): AABB = entity.boundingBox

    protected open fun resolveEntity(entity: Entity): Entity? = entity

    protected open fun shouldCacheMob(mob: Entity): Boolean = true

    protected open fun revalidateCache(): Boolean = false

    protected fun getCorrespondingMob(entity: Entity): Entity? {
        val world = entity.level()
        val box = entity.boundingBox.move(0.0, -1.0, 0.0)
        val nearby = world.getEntities(entity, box).filter { it !is ArmorStand }

        return nearby.find { candidate ->
            when (candidate) {
                is Player -> !candidate.isInvisible && candidate.uuid.version() == 2 && candidate != mc.player
                is WitherBoss -> false
                else -> true
            }
        }
    }

    fun isHighlighting(entity: Entity): Boolean {
        if (!enabled) return false
        return if (usesMobCaching()) cachedMobs.containsKey(entity) else shouldHighlight(entity)
    }

    abstract fun shouldHighlight(entity: Entity): Boolean

    abstract fun getColor(): Color

    protected open fun getColorFor(entity: Entity): Color = getColor()

    open fun usesMobCaching(): Boolean = false

    open fun shouldDrawLines(): Boolean = false

    open fun getLineColor(): Color = getColor()

    open fun getLineMode(): Int = LINE_MODE_CROSSHAIR

    open fun rendersArmor(): Boolean = false

    companion object {
        @JvmStatic
        fun isHighlightedByAny(entity: Entity): Boolean =
            modules.any { it is EntityHighlighter && it.espStyle() == EspStyle.MODEL && it.isHighlighting(entity) }
    }
}
