package gobby.pathfinder

import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.pathfinder.prediction.JumpDecision
import gobby.pathfinder.prediction.JumpPlanner
import gobby.pathfinder.prediction.JumpTracker
import gobby.pathfinder.prediction.PredictionLogger
import gobby.utils.rotation.AngleUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

internal class GroundPathSteering {
    var desiredYaw: Float = Float.NaN
        private set
    var desiredPitch: Float = Float.NaN
        private set
    private var smoothedVelX = 0.0
    private var smoothedVelZ = 0.0
    private var jumpStallTicks = 0

    fun reset() {
        desiredYaw = Float.NaN
        desiredPitch = Float.NaN
        smoothedVelX = 0.0
        smoothedVelZ = 0.0
        jumpStallTicks = 0
    }

    fun steer(waypoints: List<Vec3>, idx: Int, pos: Vec3, player: LocalPlayer, enableSpeedAdaptation: Boolean, enableSprint: Boolean) {
        updateSmoothedVelocity(player)
        val predictedPos = PathFollowMath.predictedMovementPos(pos, player, smoothedVelX, smoothedVelZ)
        val frame = PathFollowMath.groundSegmentFrame(waypoints, idx, predictedPos)
        val target = waypoints[idx]
        val targetDx = target.x - pos.x
        val targetDz = target.z - pos.z
        val targetDistance = sqrt(targetDx * targetDx + targetDz * targetDz)
        InputManager.releaseAll()
        if (target.y < pos.y - FALL_STEER_THRESHOLD) {
            steerFall(target, pos, player, targetDx, targetDz)
            return
        }
        val jump = prepareJump(waypoints, idx, pos, player, target, targetDistance, frame)
        val brakeForJump = applyJump(jump, player, target)
        if (targetDistance < ANTI_SPIN_DIST) {
            InputManager.press(MoveAction.FORWARD)
            return
        }
        applyLookAndMovement(waypoints, idx, pos, player, target, frame, predictedPos, jump, brakeForJump, enableSpeedAdaptation, enableSprint)
    }

    private fun prepareJump(waypoints: List<Vec3>, idx: Int, pos: Vec3, player: LocalPlayer, target: Vec3, targetDistance: Double, frame: PathFollowMath.GroundSegmentFrame): JumpState {
        val profile = JumpProfile.current(player)
        val requiredHeight = profile.stepHeight + STEP_JUMP_MARGIN
        val waypointDy = if (idx > 0) target.y - waypoints[idx - 1].y else target.y - pos.y
        val descending = target.y < pos.y - DESCENT_EPSILON || waypointDy < -DESCENT_EPSILON
        val reach = (JUMP_REACH_BASE + (profile.maxSkipCells - 1) * JUMP_REACH_PER_SKIP).coerceAtMost(profile.maxHorizontalBlocks + JUMP_REACH_MARGIN)
        val waypointJump = waypointDy > requiredHeight && waypointDy <= profile.maxClimb && targetDistance < reach && target.y - pos.y > requiredHeight
        val upcoming = PathJumpPlanner.findUpcoming(waypoints, idx, pos, profile)
        val upcomingTarget = upcoming?.target
        val terrainJump = !descending && (target.y - pos.y > requiredHeight || upcomingTarget != null) &&
            (PathJumpPlanner.terrainNeedsPrejump(player, frame.dirX, frame.dirZ, 1.0, profile) || PathSteeringTerrain.ledgeNeedsBridge(player, frame.dirX, frame.dirZ, 1.0, profile))
        return JumpState(profile, upcoming, upcomingTarget, target.takeIf { waypointJump }, terrainJump)
    }

    private fun applyJump(state: JumpState, player: LocalPlayer, target: Vec3): Boolean {
        if (!player.onGround()) return false
        val planned = state.upcomingTarget ?: state.waypointTarget
        if (planned == null) {
            jumpStallTicks = 0
            JumpTracker.updatePreview(null)
            if (state.terrainJump) {
                InputManager.press(MoveAction.JUMP)
                JumpTracker.register(player, null, target)
            }
            return false
        }
        val strict = state.upcoming?.isGap == true
        val plan = JumpPlanner.decide(player, planned, state.profile, strict)
        JumpTracker.updatePreview(plan.simulation)
        when (plan.decision) {
            JumpDecision.JUMP -> {
                jumpStallTicks = 0
                InputManager.press(MoveAction.JUMP)
                JumpTracker.register(player, plan.simulation, planned)
            }
            JumpDecision.BRAKE -> {
                logJumpStall(player, planned, state.profile, strict, plan.decision)
                return true
            }
            JumpDecision.WAIT -> logJumpStall(player, planned, state.profile, strict, plan.decision)
        }
        return false
    }

    private fun applyLookAndMovement(waypoints: List<Vec3>, idx: Int, pos: Vec3, player: LocalPlayer, target: Vec3, frame: PathFollowMath.GroundSegmentFrame, predictedPos: Vec3, jump: JumpState, brakeForJump: Boolean, enableSpeedAdaptation: Boolean, enableSprint: Boolean) {
        val (dx, dz) = computeSteerDelta(waypoints, idx, pos, predictedPos, frame)
        val effectiveTarget = jump.upcomingTarget ?: target
        val jumping = jump.waypointTarget != null || jump.terrainJump || jump.upcomingTarget != null
        val lookDx = effectiveTarget.x - pos.x
        val lookDz = effectiveTarget.z - pos.z
        val pitchOffset = if (jumping) 0.0 else PathSteeringTerrain.pitchFromSlope(waypoints, idx, lookDx, lookDz)
        val lookY = if (jumping) effectiveTarget.y else player.eyeY + pitchOffset
        val look = AngleUtils.calcAimAnglesFromDelta(if (jumping) lookDx else dx, lookY - player.eyeY, if (jumping) lookDz else dz)
        desiredYaw = look.first
        desiredPitch = look.second
        val movementYaw = AngleUtils.calcAimAnglesFromDelta(dx, 0.0, dz).first
        val yawDifference = abs(AngleUtils.wrapDegrees(movementYaw - player.yRot))
        val sharpTurn = PathFollowMath.upcomingTurnDegrees(waypoints, idx, pos, CORNER_BRAKE_DIST) > CORNER_BRAKE_ANGLE
        val sprintAllowed = enableSprint && !sharpTurn && !brakeForJump
        InputManager.suppressSprint = !sprintAllowed
        applyMovement(yawDifference, sprintAllowed, enableSpeedAdaptation)
    }

    private fun applyMovement(yawDifference: Float, sprintAllowed: Boolean, adaptive: Boolean) {
        if (adaptive) {
            if (yawDifference < SPEED_SPRINT_YAW) {
                InputManager.press(MoveAction.FORWARD)
                if (sprintAllowed) InputManager.press(MoveAction.SPRINT)
            } else if (yawDifference < SPEED_JOG_YAW || yawDifference < SPEED_CREEP_YAW) InputManager.press(MoveAction.FORWARD)
        } else {
            if (yawDifference < NO_ADAPT_FORWARD_YAW) InputManager.press(MoveAction.FORWARD)
            if (sprintAllowed && yawDifference < NO_ADAPT_SPRINT_YAW) InputManager.press(MoveAction.SPRINT)
        }
    }

    private fun computeSteerDelta(waypoints: List<Vec3>, idx: Int, pos: Vec3, predictedPos: Vec3, frame: PathFollowMath.GroundSegmentFrame): Pair<Double, Double> {
        val target = visibleLookaheadTarget(waypoints, idx, pos, predictedPos)
        val dx = target.x - pos.x
        val dz = target.z - pos.z
        val distanceSq = dx * dx + dz * dz
        if (distanceSq <= 0.01) return frame.dirX to frame.dirZ
        val distance = sqrt(distanceSq)
        val lateralVelocity = smoothedVelX * frame.perpX + smoothedVelZ * frame.perpZ
        val steer = PathFollowMath.dampedSteerDirection(frame, lateralVelocity, dx, dz)
        return steer.first * distance to steer.second * distance
    }

    private fun visibleLookaheadTarget(waypoints: List<Vec3>, idx: Int, pos: Vec3, predictedPos: Vec3): Vec3 {
        val speed = sqrt(smoothedVelX * smoothedVelX + smoothedVelZ * smoothedVelZ)
        var distance = PathFollowMath.lookaheadDistanceFor(speed)
        repeat(LOS_SHRINK_ATTEMPTS) {
            val target = PathFollowMath.pathLookaheadTarget(waypoints, idx, predictedPos, distance)
            if (PathCollision.quickLineOfSight(pos, target)) return target
            distance *= LOS_SHRINK_FACTOR
        }
        return PathFollowMath.centered(waypoints[idx])
    }

    private fun updateSmoothedVelocity(player: LocalPlayer) {
        val velocity = player.deltaMovement
        smoothedVelX += (velocity.x - smoothedVelX) * VELOCITY_SMOOTHING
        smoothedVelZ += (velocity.z - smoothedVelZ) * VELOCITY_SMOOTHING
    }

    private fun logJumpStall(player: LocalPlayer, target: Vec3, profile: JumpProfile, strict: Boolean, decision: JumpDecision) {
        jumpStallTicks++
        if (jumpStallTicks % JUMP_STALL_LOG_INTERVAL != 0) return
        PredictionLogger.log("[t=${player.tickCount}] JUMP_STALL ${jumpStallTicks}t decision=$decision target=${PredictionLogger.fmt(target)} ${JumpPlanner.diagnostics(player, target, profile, strict)}")
    }

    private fun steerFall(target: Vec3, pos: Vec3, player: LocalPlayer, dx: Double, dz: Double) {
        val look = AngleUtils.calcAimAnglesFromDelta(dx, target.y - player.eyeY, dz)
        desiredYaw = look.first
        desiredPitch = look.second
        val drift = abs(pos.x + player.deltaMovement.x - target.x) + abs(pos.z + player.deltaMovement.z - target.z)
        if (drift > 0.2 && abs(player.deltaMovement.y) > 0.4) InputManager.press(MoveAction.SNEAK)
        InputManager.press(MoveAction.FORWARD)
    }

    private data class JumpState(
        val profile: JumpProfile,
        val upcoming: UpcomingJump?,
        val upcomingTarget: Vec3?,
        val waypointTarget: Vec3?,
        val terrainJump: Boolean
    )
}
