package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.render.Interpolate
import gobby.utils.render.Render3D.drawEntityModel
import net.minecraft.client.render.Camera
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.boss.WitherEntity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.player.PlayerEntity
import java.awt.Color

abstract class EntityHighlighter(
    name: String,
    description: String = "",
    category: Category,
    defaultEnabled: Boolean = false
) : Module(name, description, category, toggled = true, defaultEnabled = defaultEnabled) {

    private val cachedMobs = mutableSetOf<Entity>()

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        val player = mc.player ?: return
        val world = mc.world ?: return
        if (!enabled) return

        val matrixStack = event.matrixStack
        val camera = event.camera
        val delta = event.renderTickCounter.getTickProgress(false)

        onRenderTick(event, matrixStack, camera, delta, player, world)
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!usesMobCaching() || !enabled) return
        val world = mc.world ?: return

        for (entity in world.entities) {
            if (!shouldHighlight(entity)) continue
            val mob = getCorrespondingMob(entity) ?: continue
            cachedMobs.add(mob)
        }

        cachedMobs.removeIf { !it.isAlive }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        cachedMobs.clear()
    }

    protected open fun onRenderTick(
        event: NewRender3DEvent,
        matrixStack: MatrixStack,
        camera: Camera,
        delta: Float,
        player: Entity,
        world: ClientWorld
    ) {
        if (usesMobCaching()) {
            for (entity in cachedMobs) {
                if (!entity.isAlive) continue
                renderEntity(event, matrixStack, camera, delta, entity, player)
            }
        } else {
            for (entity in world.entities) {
                if (!shouldHighlight(entity)) continue
                val resolved = resolveEntity(entity) ?: continue
                renderEntity(event, matrixStack, camera, delta, resolved, player)
            }
        }
    }

    protected open fun renderEntity(
        event: NewRender3DEvent,
        matrixStack: MatrixStack,
        camera: Camera,
        delta: Float,
        entity: Entity,
        player: Entity
    ) {
        event.drawEntityModel(matrixStack, camera, delta, entity, getColor())

        if (shouldDrawLines()) {
            val start = if (getLineMode() == 0) {
                Interpolate.interpolateEntity(player)
            } else {
                Interpolate.interpolatedLookVec()
            }
            val end = Interpolate.interpolateEntity(entity)
            drawLine3D(matrixStack, camera, start, end, getLineColor())
        }
    }

    protected open fun resolveEntity(entity: Entity): Entity? = entity

    protected fun getCorrespondingMob(entity: Entity): Entity? {
        val world = entity.entityWorld
        val box = entity.boundingBox.offset(0.0, -1.0, 0.0)
        val nearby = world.getOtherEntities(entity, box) { it !is ArmorStandEntity }

        return nearby.find { candidate ->
            when (candidate) {
                is PlayerEntity -> !candidate.isInvisible && candidate.uuid.version() == 2 && candidate != mc.player
                is WitherEntity -> false
                else -> true
            }
        }
    }

    abstract fun shouldHighlight(entity: Entity): Boolean
    abstract fun getColor(): Color

    open fun usesMobCaching(): Boolean = false
    open fun shouldDrawLines(): Boolean = false
    open fun getLineColor(): Color = getColor()
    open fun getLineMode(): Int = 1
}
