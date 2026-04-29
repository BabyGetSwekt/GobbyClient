package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.ServerTickEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.rotation.AngleUtils
import gobby.utils.skyblockID
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.BowItem
import net.minecraft.item.EnderPearlItem
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import java.awt.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object Trajectory : Module("Trajectory", "Renders the predicted impact box of bow arrows and ender pearls", Category.RENDER) {

    private val showBow by BooleanSetting("Bow", true, desc = "Render trajectory while holding a bow")
    private val showPearl by BooleanSetting("Pearl", true, desc = "Render trajectory while holding an ender pearl")
    private val boxColor by ColorSetting("Box Color", Color(0, 200, 255, 255), desc = "Color of the impact box")
    private val lineColor by ColorSetting("Line Color", Color(0, 200, 255, 200), desc = "Color of the trajectory line")
    private val simulationTicks by NumberSetting("Simulation Ticks", default = 60, min = 5, max = 200, step = 1, desc = "How far ahead to predict the trajectory in ticks")

    private const val DRAG = 0.99
    private const val ARROW_GRAVITY = 0.05
    private const val PEARL_GRAVITY = 0.03
    private const val SHORTBOW_VELOCITY = 3.0
    private const val PEARL_VELOCITY = 1.5
    private const val BOX_SIZE = 0.15
    private const val FULL_DRAW_TICKS = 20
    private const val SIDE_ARROW_Y_DROP = 0.3
    private const val TERMINATOR_SIDE_YAW = 5f
    private const val HAND_LATERAL = 0.16
    private const val HAND_Y_DROP = 0.1
    private val TERMINATOR_OFFSETS = floatArrayOf(-TERMINATOR_SIDE_YAW, 0f, TERMINATOR_SIDE_YAW)

    private var drawTicks = 0
    private var lastDrawTicks = 0

    private data class Outcome(val trail: List<Vec3d>, val impact: Vec3d?, val hitEntity: Entity?)
    private data class Collision(val point: Vec3d, val entity: Entity?)

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        val drawing = mc.player?.takeIf { it.isUsingItem && it.activeItem.item is BowItem } != null
        lastDrawTicks = drawTicks
        drawTicks = if (drawing) min(drawTicks + 1, FULL_DRAW_TICKS) else 0
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!enabled) return
        val player = mc.player ?: return
        val held = player.mainHandStack.takeUnless { it.isEmpty } ?: return

        val partial = mc.renderTickCounter.getTickProgress(false)
        val yaw = MathHelper.lerp(partial, player.lastYaw, player.yaw)
        val pitch = MathHelper.lerp(partial, player.lastPitch, player.pitch)
        val eye = player.getCameraPosVec(partial)

        when {
            showBow && held.item is BowItem -> renderBow(event, yaw, pitch, eye, held.skyblockID == "TERMINATOR")
            showPearl && held.item is EnderPearlItem -> renderPearl(event, yaw, pitch, eye)
        }
    }

    private fun renderBow(event: NewRender3DEvent, yaw: Float, pitch: Float, eye: Vec3d, isTerminator: Boolean) {
        if (isTerminator) {
            TERMINATOR_OFFSETS.forEach { offset ->
                val origin = if (offset == 0f) eye else eye.subtract(0.0, SIDE_ARROW_Y_DROP, 0.0)
                val velocity = AngleUtils.directionFromAngles(yaw + offset, pitch).multiply(SHORTBOW_VELOCITY)
                renderHit(event, simulate(origin, velocity, ARROW_GRAVITY, checkEntities = true))
            }
            return
        }
        val outcome = simulate(eye, AngleUtils.directionFromAngles(yaw, pitch).multiply(currentBowVelocity()), ARROW_GRAVITY, checkEntities = true)
        renderTrail(event, outcome.trail)
        renderHit(event, outcome)
    }

    private fun renderPearl(event: NewRender3DEvent, yaw: Float, pitch: Float, eye: Vec3d) {
        val origin = eye.add(handOffset(yaw))
        val outcome = simulate(origin, AngleUtils.directionFromAngles(yaw, pitch).multiply(PEARL_VELOCITY), PEARL_GRAVITY, checkEntities = false)
        renderTrail(event, outcome.trail)
        renderHit(event, outcome)
    }

    private fun handOffset(yaw: Float): Vec3d {
        val rad = Math.toRadians(yaw.toDouble())
        return Vec3d(-cos(rad) * HAND_LATERAL, -HAND_Y_DROP, -sin(rad) * HAND_LATERAL)
    }

    private fun renderHit(event: NewRender3DEvent, outcome: Outcome) = when {
        outcome.hitEntity != null -> draw3DBox(event.matrixStack, event.camera, outcome.hitEntity.boundingBox, boxColor, filled = false, depthTest = true)
        outcome.impact != null -> draw3DBox(event.matrixStack, event.camera, Box.of(outcome.impact, BOX_SIZE, BOX_SIZE, BOX_SIZE), boxColor, filled = true, depthTest = true)
        else -> Unit
    }

    private fun renderTrail(event: NewRender3DEvent, trail: List<Vec3d>) =
        trail.zipWithNext { a, b -> drawLine3D(event.matrixStack, event.camera, a, b, lineColor, depthTest = true) }

    private fun currentBowVelocity(): Double {
        val partial = mc.renderTickCounter.getTickProgress(false)
        val interpolated = lastDrawTicks + (drawTicks - lastDrawTicks) * partial
        return min(interpolated / FULL_DRAW_TICKS, 1f).toDouble() * 3.0
    }

    private fun simulate(start: Vec3d, initialVelocity: Vec3d, gravity: Double, checkEntities: Boolean): Outcome {
        val world = mc.world ?: return Outcome(emptyList(), null, null)
        return step(world, mutableListOf(start), start, initialVelocity, gravity, checkEntities, simulationTicks)
    }

    private tailrec fun step(
        world: ClientWorld,
        trail: MutableList<Vec3d>,
        pos: Vec3d,
        vel: Vec3d,
        gravity: Double,
        checkEntities: Boolean,
        ticksLeft: Int
    ): Outcome {
        if (ticksLeft == 0) return Outcome(trail, null, null)
        val next = pos.add(vel)
        nearestCollision(world, pos, next, checkEntities)?.let {
            trail += it.point
            return Outcome(trail, it.point, it.entity)
        }
        trail += next
        return step(world, trail, next, decay(vel, gravity), gravity, checkEntities, ticksLeft - 1)
    }

    private fun decay(vel: Vec3d, gravity: Double) = Vec3d(vel.x * DRAG, vel.y * DRAG - gravity, vel.z * DRAG)

    private fun nearestCollision(world: ClientWorld, from: Vec3d, to: Vec3d, checkEntities: Boolean): Collision? {
        val player = mc.player
        val blockRay = world.raycast(RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player))
        val blockHit = blockRay.pos.takeIf { blockRay.type == HitResult.Type.BLOCK }

        if (!checkEntities) return blockHit?.let { Collision(it, null) }

        val blockDistSq = blockHit?.let { from.squaredDistanceTo(it) } ?: Double.MAX_VALUE
        val (entity, entityHit) = world.getOtherEntities(player, Box(from, to)) {
            it.isAlive && it !is ArmorStandEntity && it !is PersistentProjectileEntity
        }.asSequence()
            .mapNotNull { e -> e.boundingBox.raycast(from, to).orElse(null)?.let { e to it } }
            .minByOrNull { from.squaredDistanceTo(it.second) }
            ?: (null to null)

        return when {
            entity != null && entityHit != null && from.squaredDistanceTo(entityHit) < blockDistSq -> Collision(entityHit, entity)
            blockHit != null -> Collision(blockHit, null)
            else -> null
        }
    }
}
