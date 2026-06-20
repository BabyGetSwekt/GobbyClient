package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.render.Interpolate
import gobby.utils.render.Render3D.drawEntityModel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import java.awt.Color

private const val LINE_MODE_FEET = 0
private const val LINE_MODE_CROSSHAIR = 1

abstract class EntityHighlighter(
    name: String,
    description: String = "",
    category: Category,
    defaultEnabled: Boolean = false
) : Module(name, description, category, toggled = true, defaultEnabled = defaultEnabled) {

    private val cachedMobs = mutableSetOf<Entity>()

    @SubscribeEvent
    fun onRender(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity || !enabled) return
        val context = event.context
        val poseStack = context.poseStack()
        val collector = context.submitNodeCollector()
        val camera = mc.gameRenderer.mainCamera()
        val delta = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val color = getColor()
        val lineColor = getLineColor()
        val player = mc.player
        val lineStart = if (shouldDrawLines() && player != null) {
            if (getLineMode() == LINE_MODE_FEET) Interpolate.interpolateEntity(player) else Interpolate.interpolatedLookVec()
        } else null

        forEachHighlight { entity ->
            drawEntityModel(poseStack, collector, camera, delta, entity, color, rendersArmor())
            lineStart?.let { drawLine3D(poseStack, camera, it, Interpolate.interpolateEntity(entity), lineColor) }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!usesMobCaching() || !enabled) return
        val world = mc.level ?: return

        for (entity in world.entitiesForRendering()) {
            if (!shouldHighlight(entity)) continue
            val mob = getCorrespondingMob(entity) ?: continue
            if (!shouldCacheMob(mob)) continue
            cachedMobs.add(mob)
        }

        cachedMobs.removeIf { !it.isAlive }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        cachedMobs.clear()
    }

    private inline fun forEachHighlight(action: (Entity) -> Unit) {
        if (usesMobCaching()) {
            cachedMobs.forEach { if (it.isAlive) action(it) }
        } else {
            val world = mc.level ?: return
            world.entitiesForRendering().forEach { entity ->
                if (!shouldHighlight(entity)) return@forEach
                action(resolveEntity(entity) ?: return@forEach)
            }
        }
    }

    protected open fun resolveEntity(entity: Entity): Entity? = entity

    protected open fun shouldCacheMob(mob: Entity): Boolean = true

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
        return if (usesMobCaching()) cachedMobs.contains(entity) else shouldHighlight(entity)
    }

    abstract fun shouldHighlight(entity: Entity): Boolean
    abstract fun getColor(): Color

    open fun usesMobCaching(): Boolean = false
    open fun shouldDrawLines(): Boolean = false
    open fun getLineColor(): Color = getColor()
    open fun getLineMode(): Int = LINE_MODE_CROSSHAIR
    open fun rendersArmor(): Boolean = false

    companion object {
        @JvmStatic
        fun isHighlightedByAny(entity: Entity): Boolean =
            modules.any { it is EntityHighlighter && it.isHighlighting(entity) }
    }
}
