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
import net.minecraft.client.Camera
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
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
        val world = mc.level ?: return
        if (!enabled) return

        val matrixStack = event.matrixStack
        val camera = event.camera
        val delta = event.renderTickCounter.getGameTimeDeltaPartialTick(false)

        onRenderTick(event, matrixStack, camera, delta, player, world)
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!usesMobCaching() || !enabled) return
        val world = mc.level ?: return

        for (entity in world.entitiesForRendering()) {
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
        matrixStack: PoseStack,
        camera: Camera,
        delta: Float,
        player: Entity,
        world: ClientLevel
    ) {
        if (usesMobCaching()) {
            for (entity in cachedMobs) {
                if (!entity.isAlive) continue
                renderEntity(event, matrixStack, camera, delta, entity, player)
            }
        } else {
            for (entity in world.entitiesForRendering()) {
                if (!shouldHighlight(entity)) continue
                val resolved = resolveEntity(entity) ?: continue
                renderEntity(event, matrixStack, camera, delta, resolved, player)
            }
        }
    }

    protected open fun renderEntity(
        event: NewRender3DEvent,
        matrixStack: PoseStack,
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

    abstract fun shouldHighlight(entity: Entity): Boolean
    abstract fun getColor(): Color

    open fun usesMobCaching(): Boolean = false
    open fun shouldDrawLines(): Boolean = false
    open fun getLineColor(): Color = getColor()
    open fun getLineMode(): Int = 1
}
