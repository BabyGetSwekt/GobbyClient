package gobby.pathfinder.prediction

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

object JumpTracker {

    private const val LANDING_ERROR_TOLERANCE = 0.75
    private const val MIN_AIR_TICKS_BEFORE_LANDING = 2
    private const val TIMEOUT_TICKS = 80

    private data class PendingJump(
        val predicted: JumpSimulation?,
        val from: Vec3,
        val target: Vec3,
        val startTick: Int
    )

    private var pending: PendingJump? = null
    private var previewPositions: List<Vec3> = emptyList()
    private val actualPositions = ArrayList<Vec3>()

    fun updatePreview(simulation: JumpSimulation?) {
        previewPositions = simulation?.tickPositions ?: emptyList()
    }

    fun renderPositions(): List<Vec3> =
        pending?.predicted?.tickPositions ?: previewPositions

    fun isPending(): Boolean = pending != null

    fun register(player: LocalPlayer, simulation: JumpSimulation?, target: Vec3) {
        if (pending != null) {
            PredictionLogger.log("[t=${player.tickCount}] REJUMP while previous jump still in flight")
            return
        }
        pending = PendingJump(simulation, player.position(), target, player.tickCount)
        actualPositions.clear()
        val predictedText = simulation?.landing?.let { "predicted=${PredictionLogger.fmt(it)} in ${simulation.airTicks}t" } ?: "predicted=none"
        PredictionLogger.log("[t=${player.tickCount}] JUMP from ${PredictionLogger.fmt(player.position())} target=${PredictionLogger.fmt(target)} sprint=${player.isSprinting} $predictedText")
    }

    fun tick(player: LocalPlayer) {
        val jump = pending ?: return
        val elapsed = player.tickCount - jump.startTick
        if (elapsed > TIMEOUT_TICKS) {
            PredictionLogger.log("[t=${player.tickCount}] JUMP_TIMEOUT no landing within ${TIMEOUT_TICKS}t")
            pending = null
            return
        }
        if (elapsed >= 1) actualPositions += player.position()
        if (elapsed < MIN_AIR_TICKS_BEFORE_LANDING || !player.onGround()) return

        val actual = player.position()
        val predicted = jump.predicted?.landing
        if (predicted == null) {
            PredictionLogger.log("[t=${player.tickCount}] LAND actual=${PredictionLogger.fmt(actual)} after ${elapsed}t (no prediction)")
        } else {
            val dx = actual.x - predicted.x
            val dz = actual.z - predicted.z
            val planarError = sqrt(dx * dx + dz * dz)
            val yError = abs(actual.y - predicted.y)
            val verdict = if (planarError > LANDING_ERROR_TOLERANCE || yError > LANDING_ERROR_TOLERANCE) "MISPREDICT" else "OK"
            val predictedTicks = jump.predicted.airTicks
            PredictionLogger.log(
                "[t=${player.tickCount}] LAND actual=${PredictionLogger.fmt(actual)} in ${elapsed}t (pred ${predictedTicks}t) err=%.2f yErr=%.2f ${tickErrorSummary(jump)} $verdict"
                    .format(planarError, yError)
            )
        }
        pending = null
    }

    private fun tickErrorSummary(jump: PendingJump): String {
        val predictedTicks = jump.predicted?.tickPositions ?: return ""
        val compared = min(predictedTicks.size, actualPositions.size)
        if (compared == 0) return ""
        var maxError = 0.0
        var totalError = 0.0
        for (i in 0 until compared) {
            val error = predictedTicks[i].distanceTo(actualPositions[i])
            totalError += error
            if (error > maxError) maxError = error
        }
        return "tickErr avg=%.2f max=%.2f over ${compared}t".format(totalError / compared, maxError)
    }

    fun reset() {
        pending = null
        previewPositions = emptyList()
        actualPositions.clear()
    }
}
