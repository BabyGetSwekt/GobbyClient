package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.pathfinder.prediction.JumpTracker
import gobby.pathfinder.prediction.PredictionLogger
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

internal class PathRecoveryMonitor {
    private var lastCursor: Int = 0
    private var noProgressTicks: Int = 0
    private var positionStuckTicks: Int = 0
    private var lastPosForStuck: Vec3? = null
    private var inRecovery: Boolean = false
    private var recoveryTicks: Int = 0
    private var recoveryStrafeRight: Boolean = true
    private var offPathTicks: Int = 0

    sealed class Decision {
        data object None : Decision()
        data class SkipTo(val cursor: Int) : Decision()
        data object OffPath : Decision()
        data object Stuck : Decision()
    }

    fun reset() {
        lastCursor = 0
        noProgressTicks = 0
        positionStuckTicks = 0
        lastPosForStuck = null
        inRecovery = false
        recoveryTicks = 0
        offPathTicks = 0
    }

    fun clearForFall() {
        inRecovery = false
        noProgressTicks = 0
        positionStuckTicks = 0
        offPathTicks = 0
    }

    fun tick(pos: Vec3, isSky: Boolean, cursor: Int, waypoints: List<Vec3>, repathInFlight: Boolean): Decision {
        val airborne = !isSky && mc.player?.onGround() == false
        if (!repathInFlight) handleOffPath(pos, cursor, waypoints, airborne)?.let { return it }
        if (airborne) return Decision.None
        if (cursor != lastCursor) {
            resetProgress(cursor)
            return Decision.None
        }
        updateProgress(pos)
        return stuckDecision(repathInFlight)
    }

    private fun handleOffPath(pos: Vec3, cursor: Int, waypoints: List<Vec3>, airborne: Boolean): Decision? {
        if (!isOffPath(pos, cursor, waypoints)) {
            if (!airborne) offPathTicks = 0
            return null
        }
        nearestReachableWaypoint(pos, cursor, waypoints)?.let {
            offPathTicks = 0
            return Decision.SkipTo(it)
        }
        offPathTicks++
        if (offPathTicks < OFF_PATH_CONFIRM_TICKS) return null
        offPathTicks = 0
        return Decision.OffPath
    }

    private fun resetProgress(cursor: Int) {
        lastCursor = cursor
        noProgressTicks = 0
        positionStuckTicks = 0
        lastPosForStuck = null
        clearRecoveryInputs()
    }

    private fun updateProgress(pos: Vec3) {
        noProgressTicks++
        val last = lastPosForStuck
        if (last != null) {
            val dx = pos.x - last.x
            val dz = pos.z - last.z
            val movedSq = dx * dx + dz * dz
            positionStuckTicks = if (movedSq < STUCK_MOVE_EPSILON_SQ) positionStuckTicks + 1 else max(0, positionStuckTicks - 2)
        }
        lastPosForStuck = pos
    }

    private fun stuckDecision(repathInFlight: Boolean): Decision {
        val stuck = noProgressTicks >= STUCK_STRAFE_THRESHOLD && positionStuckTicks >= STUCK_STRAFE_THRESHOLD / 2
        val veryStuck = noProgressTicks >= STUCK_REPATH_THRESHOLD && positionStuckTicks >= STUCK_REPATH_THRESHOLD / 2
        if (veryStuck && !repathInFlight) return Decision.Stuck
        if (stuck && !inRecovery) {
            inRecovery = true
            recoveryTicks = 0
            recoveryStrafeRight = Random.nextBoolean()
        }
        return Decision.None
    }

    fun applyRecoveryInputsIfNeeded(): Boolean {
        if (!inRecovery) return false
        InputManager.releaseAll()
        recoveryTicks++
        when {
            recoveryTicks <= RECOVERY_JUMP_TICKS -> {
                if (recoveryTicks == 1) mc.player?.let {
                    PredictionLogger.log("[t=${it.tickCount}] RECOVERY_JUMP at ${PredictionLogger.fmt(it.position())}")
                    JumpTracker.register(it, null, it.position())
                }
                InputManager.press(MoveAction.FORWARD)
                InputManager.press(MoveAction.JUMP)
            }
            recoveryTicks <= RECOVERY_JUMP_TICKS + RECOVERY_BACKSTEP_TICKS -> InputManager.press(MoveAction.BACKWARD)
            else -> {
                val phase = recoveryTicks - RECOVERY_JUMP_TICKS - RECOVERY_BACKSTEP_TICKS
                if (phase % STRAFE_WIGGLE_PERIOD == 0) recoveryStrafeRight = !recoveryStrafeRight
                if (recoveryStrafeRight) InputManager.press(MoveAction.RIGHT) else InputManager.press(MoveAction.LEFT)
                InputManager.press(MoveAction.FORWARD)
                InputManager.press(MoveAction.JUMP)
            }
        }
        return true
    }

    private fun clearRecoveryInputs() {
        if (!inRecovery) return
        inRecovery = false
        recoveryTicks = 0
        InputManager.release(MoveAction.LEFT)
        InputManager.release(MoveAction.RIGHT)
        InputManager.release(MoveAction.BACKWARD)
    }

    private fun nearestReachableWaypoint(pos: Vec3, cursor: Int, waypoints: List<Vec3>): Int? {
        if (cursor >= waypoints.size) return null
        val maxLook = minOf(cursor + 6, waypoints.lastIndex)
        var bestIdx = -1
        var bestDistSq = OFF_PATH_SKIP_DIST_SQ
        for (i in (cursor + 1)..maxLook) {
            val wp = waypoints[i]
            val dx = pos.x - wp.x
            val dz = pos.z - wp.z
            if (wp.y - pos.y > WAYPOINT_CLIMB_TOLERANCE) continue
            if (pos.y - wp.y > OFF_PATH_Y_DIFF) continue
            val d = dx * dx + dz * dz
            if (d < bestDistSq) {
                bestDistSq = d
                bestIdx = i
            }
        }
        return if (bestIdx > cursor) bestIdx else null
    }

    private fun isOffPath(pos: Vec3, cursor: Int, waypoints: List<Vec3>): Boolean {
        if (cursor >= waypoints.size) return false
        val deviation = PathFollowMath.routeDeviation(
            waypoints,
            cursor,
            pos,
            OFF_PATH_CORRIDOR_BEHIND,
            OFF_PATH_CORRIDOR_AHEAD
        )
        if (deviation != null) {
            return deviation.lateral > OFF_PATH_LATERAL_DIST || abs(deviation.verticalBelow) > OFF_PATH_Y_DIFF
        }
        val tgt = waypoints[cursor]
        val dx = pos.x - tgt.x
        val dz = pos.z - tgt.z
        val dy = pos.y - tgt.y
        return (dx * dx + dz * dz) > OFF_PATH_SKIP_DIST_SQ || abs(dy) > OFF_PATH_Y_DIFF
    }
}
