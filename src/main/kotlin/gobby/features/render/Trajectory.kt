package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.ServerTickEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.events.render.renderTickCounter
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.BowSimulator
import gobby.utils.isShortbow
import gobby.utils.render.BlockRenderUtils.draw3DBox
import gobby.utils.render.BlockRenderUtils.drawLine3D
import gobby.utils.rotation.AngleUtils
import gobby.utils.skyblockID
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.EnderpearlItem
import net.minecraft.world.phys.AABB
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.awt.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object Trajectory : Module("Trajectory", "Renders the predicted impact box of bow arrows and ender pearls", Category.RENDER) {

    private val showBow by BooleanSetting("Bow", true, desc = "Render trajectory while holding a bow")
    private val showPearl by BooleanSetting("Pearl", true, desc = "Render trajectory while holding an ender pearl")
    private val boxColor by ColorSetting("AABB Color", Color(0, 200, 255, 255), desc = "Color of the impact box")
    private val lineColor by ColorSetting("Line Color", Color(0, 200, 255, 200), desc = "Color of the trajectory line")
    private val simulationTicks by NumberSetting("Simulation Ticks", default = 60, min = 5, max = 200, step = 1, desc = "How far ahead to predict the trajectory in ticks")

    private const val BOX_SIZE = 0.15
    private const val FULL_DRAW_TICKS = 20
    private const val SIDE_ARROW_Y_DROP = 0.3
    private const val TERMINATOR_SIDE_YAW = 5f
    private const val HAND_LATERAL = 0.16
    private const val HAND_Y_DROP = 0.1
    private val TERMINATOR_OFFSETS = floatArrayOf(-TERMINATOR_SIDE_YAW, 0f, TERMINATOR_SIDE_YAW)

    private var drawTicks = 0
    private var lastDrawTicks = 0

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent) {
        val drawing = mc.player?.takeIf { it.isUsingItem && it.useItem.item is BowItem } != null
        lastDrawTicks = drawTicks
        drawTicks = if (drawing) min(drawTicks + 1, FULL_DRAW_TICKS) else 0
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity) return
        if (!enabled) return
        val player = mc.player ?: return
        val held = player.mainHandItem.takeUnless { it.isEmpty } ?: return

        val partial = event.renderTickCounter.getGameTimeDeltaPartialTick(false)
        val yaw = Mth.lerp(partial, player.yRotO, player.yRot)
        val pitch = Mth.lerp(partial, player.xRotO, player.xRot)
        val eye = player.getEyePosition(partial)

        when {
            showBow && held.item is BowItem -> renderBow(event, yaw, pitch, eye, held.isShortbow(), held.skyblockID == "TERMINATOR")
            showPearl && held.item is EnderpearlItem -> renderPearl(event, yaw, pitch, eye)
        }
    }

    private fun renderBow(event: Render3DEvent, yaw: Float, pitch: Float, eye: Vec3, isShortbow: Boolean, isTerminator: Boolean) {
        if (isTerminator) {
            TERMINATOR_OFFSETS.forEach { offset ->
                val origin = if (offset == 0f) eye else eye.subtract(0.0, SIDE_ARROW_Y_DROP, 0.0)
                val velocity = AngleUtils.directionFromAngles(yaw + offset, pitch).scale(BowSimulator.SHORTBOW_VELOCITY)
                renderHit(event, BowSimulator.simulate(origin, velocity, BowSimulator.ARROW_GRAVITY, simulationTicks, checkEntities = true))
            }
            return
        }
        val velocity = if (isShortbow) BowSimulator.SHORTBOW_VELOCITY else currentBowVelocity()
        val outcome = BowSimulator.simulate(eye, AngleUtils.directionFromAngles(yaw, pitch).scale(velocity), BowSimulator.ARROW_GRAVITY, simulationTicks, checkEntities = true)
        renderTrail(event, outcome.trail)
        renderHit(event, outcome)
    }

    private fun renderPearl(event: Render3DEvent, yaw: Float, pitch: Float, eye: Vec3) {
        val origin = eye.add(handOffset(yaw))
        val outcome = BowSimulator.simulate(origin, AngleUtils.directionFromAngles(yaw, pitch).scale(BowSimulator.PEARL_VELOCITY), BowSimulator.PEARL_GRAVITY, simulationTicks, checkEntities = false)
        renderTrail(event, outcome.trail)
        renderHit(event, outcome)
    }

    private fun handOffset(yaw: Float): Vec3 {
        val rad = Math.toRadians(yaw.toDouble())
        return Vec3(-cos(rad) * HAND_LATERAL, -HAND_Y_DROP, -sin(rad) * HAND_LATERAL)
    }

    private fun renderHit(event: Render3DEvent, outcome: BowSimulator.Outcome) {
        outcome.hitEntity?.let { entity ->
            draw3DBox(event.matrixStack, event.camera, entity.boundingBox, boxColor, filled = false, depthTest = true)
            return
        }
        outcome.impact?.let { impact ->
            draw3DBox(event.matrixStack, event.camera, AABB.ofSize(impact, BOX_SIZE, BOX_SIZE, BOX_SIZE), boxColor, filled = true, depthTest = true)
        }
    }

    private fun renderTrail(event: Render3DEvent, trail: List<Vec3>) =
        trail.zipWithNext { a, b -> drawLine3D(event.matrixStack, event.camera, a, b, lineColor, depthTest = true) }

    private fun currentBowVelocity(): Double {
        val partial = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val interpolated = lastDrawTicks + (drawTicks - lastDrawTicks) * partial
        return min(interpolated / FULL_DRAW_TICKS, 1f).toDouble() * 3.0
    }
}
