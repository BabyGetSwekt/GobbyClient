package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.pathfinder.prediction.JumpDecision
import gobby.pathfinder.prediction.JumpPlanner
import gobby.pathfinder.prediction.JumpTracker
import gobby.pathfinder.prediction.PredictionLogger
import gobby.pathfinder.world.BlockCache
import gobby.utils.rotation.AngleUtils
import gobby.utils.rotation.RotationUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal class PathSteering {
    private var desiredYaw: Float = Float.NaN
    private var desiredPitch: Float = Float.NaN
    private var smoothedVelX: Double = 0.0
    private var smoothedVelZ: Double = 0.0
    private var jumpStallTicks: Int = 0

    fun reset() {
        desiredYaw = Float.NaN
        desiredPitch = Float.NaN
        smoothedVelX = 0.0
        smoothedVelZ = 0.0
        jumpStallTicks = 0
    }

    private fun updateSmoothedVelocity(player: LocalPlayer) {
        val vel = player.deltaMovement
        smoothedVelX += (vel.x - smoothedVelX) * VELOCITY_SMOOTHING
        smoothedVelZ += (vel.z - smoothedVelZ) * VELOCITY_SMOOTHING
    }

    fun steerSky(target: Vec3, pos: Vec3, player: LocalPlayer) {
        val dx = target.x - pos.x
        val dz = target.z - pos.z
        val dy = target.y - pos.y
        val horizontalDist = sqrt(dx * dx + dz * dz)

        if (horizontalDist > 0.05) {
            val angles = AngleUtils.calcAimAnglesFromDelta(dx, dy, dz)
            desiredYaw = angles.first
            desiredPitch = angles.second
        } else if (!player.onGround() && dy < -1.0) {
            desiredPitch = DROP_LOOK_PITCH
        }

        InputManager.releaseAll()

        if (isAheadHeadBlocked(pos, dx, dz, horizontalDist)) {
            InputManager.press(MoveAction.SNEAK)
            InputManager.press(MoveAction.FORWARD)
            return
        }

        val yawError = if (desiredYaw.isNaN()) 0f else abs(AngleUtils.wrapDegrees(desiredYaw - player.yRot))
        if (yawError < SPEED_SPRINT_YAW || horizontalDist < ANTI_SPIN_DIST) {
            InputManager.press(MoveAction.FORWARD)
        }

        when {
            dy > JUMP_HOLD_THRESHOLD -> InputManager.press(MoveAction.JUMP)
            dy < -SNEAK_HOLD_THRESHOLD -> InputManager.press(MoveAction.SNEAK)
        }
    }

    fun steerGround(
        waypoints: List<Vec3>,
        idx: Int,
        pos: Vec3,
        player: LocalPlayer,
        enableSpeedAdaptation: Boolean,
        enableSprint: Boolean
    ) {
        val target = waypoints[idx]
        updateSmoothedVelocity(player)
        val predictedPos = PathFollowMath.predictedMovementPos(pos, player, smoothedVelX, smoothedVelZ)
        val frame = PathFollowMath.groundSegmentFrame(waypoints, idx, predictedPos)
        val targetDx = target.x - pos.x
        val targetDz = target.z - pos.z
        val targetHorizontalDist = sqrt(targetDx * targetDx + targetDz * targetDz)

        InputManager.releaseAll()

        if (target.y < pos.y - FALL_STEER_THRESHOLD) {
            steerFall(target, pos, player, targetDx, targetDz)
            return
        }

        val jumpProfile = JumpProfile.current(player)
        val jumpRequiredHeight = jumpProfile.stepHeight + STEP_JUMP_MARGIN
        val waypointDy = if (idx > 0) target.y - waypoints[idx - 1].y else target.y - pos.y
        val descendingSegment = target.y < pos.y - DESCENT_EPSILON || waypointDy < -DESCENT_EPSILON
        val jumpReachDist = (JUMP_REACH_BASE + (jumpProfile.maxSkipCells - 1) * JUMP_REACH_PER_SKIP)
            .coerceAtMost(jumpProfile.maxHorizontalBlocks + JUMP_REACH_MARGIN)
        val needsWaypointJump = waypointDy > jumpRequiredHeight && waypointDy <= jumpProfile.maxClimb &&
            targetHorizontalDist < jumpReachDist && (target.y - pos.y) > jumpRequiredHeight
        val upcomingJump = findUpcomingJump(waypoints, idx, pos, jumpProfile)
        val upcomingJumpTarget = upcomingJump?.target
        val climbAheadOfPlayer = target.y - pos.y > jumpRequiredHeight || upcomingJumpTarget != null
        val needsTerrainJump = !descendingSegment && climbAheadOfPlayer &&
            (terrainNeedsPrejump(player, frame.dirX, frame.dirZ, 1.0, jumpProfile) ||
                ledgeNeedsBridge(player, frame.dirX, frame.dirZ, 1.0, jumpProfile))

        val (dx, dz) = computeSteerDelta(waypoints, idx, pos, predictedPos, frame)

        var brakeForJump = false
        val plannedJumpTarget = upcomingJumpTarget ?: target.takeIf { needsWaypointJump }
        if (player.onGround()) {
            when {
                plannedJumpTarget != null -> {
                    val strict = upcomingJump?.isGap == true
                    val plan = JumpPlanner.decide(player, plannedJumpTarget, jumpProfile, strict)
                    JumpTracker.updatePreview(plan.simulation)
                    when (plan.decision) {
                        JumpDecision.JUMP -> {
                            jumpStallTicks = 0
                            InputManager.press(MoveAction.JUMP)
                            JumpTracker.register(player, plan.simulation, plannedJumpTarget)
                        }
                        JumpDecision.BRAKE -> {
                            brakeForJump = true
                            logJumpStall(player, plannedJumpTarget, jumpProfile, strict, plan.decision)
                        }
                        JumpDecision.WAIT -> logJumpStall(player, plannedJumpTarget, jumpProfile, strict, plan.decision)
                    }
                }
                else -> {
                    jumpStallTicks = 0
                    JumpTracker.updatePreview(null)
                    if (needsTerrainJump) {
                        InputManager.press(MoveAction.JUMP)
                        JumpTracker.register(player, null, target)
                    }
                }
            }
        }

        if (targetHorizontalDist < ANTI_SPIN_DIST) {
            InputManager.press(MoveAction.FORWARD)
            return
        }

        val effectiveTarget = upcomingJumpTarget ?: target
        val effectiveJumping = needsWaypointJump || needsTerrainJump || upcomingJumpTarget != null
        val edx = effectiveTarget.x - pos.x
        val edz = effectiveTarget.z - pos.z
        val (lookDx, lookDz) = if (effectiveJumping) edx to edz else dx to dz
        val pitchOffset = if (effectiveJumping) 0.0 else computePitchFromSlope(waypoints, idx, lookDx, lookDz)
        val lookTargetY = if (effectiveJumping) effectiveTarget.y else (player.eyeY + pitchOffset)
        val lookAngles = AngleUtils.calcAimAnglesFromDelta(lookDx, lookTargetY - player.eyeY, lookDz)
        setGroundLookTarget(lookAngles.first, lookAngles.second)

        val movementYaw = AngleUtils.calcAimAnglesFromDelta(dx, 0.0, dz).first
        val absYawDiff = abs(AngleUtils.wrapDegrees(movementYaw - player.yRot))

        val sharpTurnAhead = PathFollowMath.upcomingTurnDegrees(waypoints, idx, pos, CORNER_BRAKE_DIST) > CORNER_BRAKE_ANGLE
        val sprintAllowed = enableSprint && !sharpTurnAhead && !brakeForJump
        InputManager.suppressSprint = !sprintAllowed

        if (enableSpeedAdaptation) {
            when {
                absYawDiff < SPEED_SPRINT_YAW -> {
                    InputManager.press(MoveAction.FORWARD)
                    if (sprintAllowed) InputManager.press(MoveAction.SPRINT)
                }
                absYawDiff < SPEED_JOG_YAW -> InputManager.press(MoveAction.FORWARD)
                absYawDiff < SPEED_CREEP_YAW -> InputManager.press(MoveAction.FORWARD)
            }
        } else {
            if (absYawDiff < NO_ADAPT_FORWARD_YAW) InputManager.press(MoveAction.FORWARD)
            if (sprintAllowed && absYawDiff < NO_ADAPT_SPRINT_YAW) InputManager.press(MoveAction.SPRINT)
        }
    }

    private fun computeSteerDelta(
        waypoints: List<Vec3>,
        idx: Int,
        pos: Vec3,
        predictedPos: Vec3,
        frame: PathFollowMath.GroundSegmentFrame
    ): Pair<Double, Double> {
        val lookTarget = visibleLookaheadTarget(waypoints, idx, pos, predictedPos)
        val lookaheadDx = lookTarget.x - pos.x
        val lookaheadDz = lookTarget.z - pos.z
        val lookaheadDistSq = lookaheadDx * lookaheadDx + lookaheadDz * lookaheadDz
        if (lookaheadDistSq <= 0.01) return frame.dirX to frame.dirZ
        val dist = sqrt(lookaheadDistSq)
        val lateralVel = smoothedVelX * frame.perpX + smoothedVelZ * frame.perpZ
        val steer = PathFollowMath.dampedSteerDirection(frame, lateralVel, lookaheadDx, lookaheadDz)
        return steer.first * dist to steer.second * dist
    }

    private fun visibleLookaheadTarget(waypoints: List<Vec3>, idx: Int, pos: Vec3, predictedPos: Vec3): Vec3 {
        val speedPerTick = sqrt(smoothedVelX * smoothedVelX + smoothedVelZ * smoothedVelZ)
        var lookDist = PathFollowMath.lookaheadDistanceFor(speedPerTick)
        repeat(LOS_SHRINK_ATTEMPTS) {
            val candidate = PathFollowMath.pathLookaheadTarget(waypoints, idx, predictedPos, lookDist)
            if (PathCollision.quickLineOfSight(pos, candidate)) return candidate
            lookDist *= LOS_SHRINK_FACTOR
        }
        return PathFollowMath.centered(waypoints[idx])
    }

    fun applyYawEasing() {
        if (desiredYaw.isNaN() || desiredPitch.isNaN()) return
        val frameTicks = mc.deltaTracker.realtimeDeltaTicks
        RotationUtils.easeTowards(
            desiredYaw,
            desiredPitch,
            frameEaseFactor(PATH_YAW_EASE_PER_TICK, frameTicks),
            frameEaseFactor(PATH_PITCH_EASE_PER_TICK, frameTicks)
        )
    }

    private fun frameEaseFactor(perTickFactor: Float, frameTicks: Float): Float =
        1f - (1f - perTickFactor).pow(frameTicks)

    private fun isAheadHeadBlocked(pos: Vec3, dx: Double, dz: Double, horizontalDist: Double): Boolean {
        val headAbove = BlockPos(floor(pos.x).toInt(), floor(pos.y + BlockCache.PLAYER_HEIGHT).toInt(), floor(pos.z).toInt())
        if (!BlockCache.isPassable(headAbove)) return true
        if (horizontalDist < 0.05) return false
        val nx = dx / horizontalDist
        val nz = dz / horizontalDist
        val headAhead = BlockPos(
            floor(pos.x + nx * AHEAD_HEAD_PROBE_DIST).toInt(),
            floor(pos.y + BlockCache.PLAYER_HEIGHT - 0.3).toInt(),
            floor(pos.z + nz * AHEAD_HEAD_PROBE_DIST).toInt()
        )
        return !BlockCache.isPassable(headAhead)
    }

    private fun setGroundLookTarget(yaw: Float, pitch: Float) {
        desiredYaw = yaw
        desiredPitch = pitch
    }

    private fun logJumpStall(player: LocalPlayer, target: Vec3, jumpProfile: JumpProfile, strict: Boolean, decision: JumpDecision) {
        jumpStallTicks++
        if (jumpStallTicks % JUMP_STALL_LOG_INTERVAL != 0) return
        PredictionLogger.log(
            "[t=${player.tickCount}] JUMP_STALL ${jumpStallTicks}t decision=$decision target=${PredictionLogger.fmt(target)} ${JumpPlanner.diagnostics(player, target, jumpProfile, strict)}"
        )
    }

    private data class UpcomingJump(val target: Vec3, val isGap: Boolean)

    private fun findUpcomingJump(waypoints: List<Vec3>, idx: Int, pos: Vec3, jumpProfile: JumpProfile): UpcomingJump? {
        val jumpThreshold = jumpProfile.stepHeight + STEP_JUMP_MARGIN
        val ceiling = min(idx + max(JUMP_LOOKAHEAD_WAYPOINTS, jumpProfile.maxSkipCells), waypoints.lastIndex)
        for (i in idx..ceiling) {
            val wp = waypoints[i]
            val prev = if (i > 0) waypoints[i - 1] else pos
            val dy = wp.y - prev.y
            if (dy <= jumpThreshold || dy > jumpProfile.maxClimb) continue
            if (wp.y - pos.y <= jumpThreshold) continue
            if (stepReachable(floor(wp.x).toInt(), floor(wp.z).toInt(), prev.y, wp.y, wp.x - prev.x, wp.z - prev.z, jumpProfile)) continue
            val dx = wp.x - pos.x
            val dz = wp.z - pos.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist <= max(JUMP_LOOKAHEAD_DIST, jumpProfile.maxHorizontalBlocks + JUMP_REACH_MARGIN)) {
                val pairDx = wp.x - prev.x
                val pairDz = wp.z - prev.z
                val isGap = sqrt(pairDx * pairDx + pairDz * pairDz) > ADJACENT_CELL_DIST
                return UpcomingJump(wp, isGap)
            }
        }
        return null
    }

    private fun stepReachable(x: Int, z: Int, fromFeetY: Double, toFeetY: Double, dirX: Double, dirZ: Double, jumpProfile: JumpProfile): Boolean {
        val state = BlockCache.getBlockState(BlockPos(x, floor(fromFeetY + GROUND_TOLERANCE).toInt(), z))
        if (state.block is StairBlock) {
            if (state.getValue(StairBlock.HALF) != Half.BOTTOM) return false
            val facing = state.getValue(StairBlock.FACING)
            return facing.stepX * dirX + facing.stepZ * dirZ > 0.0
        }
        val surfaces = BlockCache.getStandableSurfaces(x, z, fromFeetY + GROUND_TOLERANCE, toFeetY + GROUND_TOLERANCE)
        val lowest = surfaces.minByOrNull { it.feetY } ?: return false
        return lowest.feetY - fromFeetY <= jumpProfile.stepHeight + STEP_JUMP_MARGIN
    }

    private fun steerFall(target: Vec3, pos: Vec3, player: LocalPlayer, dx: Double, dz: Double) {
        val lookAngles = AngleUtils.calcAimAnglesFromDelta(dx, target.y - player.eyeY, dz)
        desiredYaw = lookAngles.first
        desiredPitch = lookAngles.second
        val xzDrift = abs(pos.x + player.deltaMovement.x - target.x) + abs(pos.z + player.deltaMovement.z - target.z)
        if (xzDrift > 0.2 && abs(player.deltaMovement.y) > 0.4) InputManager.press(MoveAction.SNEAK)
        InputManager.press(MoveAction.FORWARD)
    }

    private fun computePitchFromSlope(waypoints: List<Vec3>, idx: Int, lookDx: Double, lookDz: Double): Double {
        if (idx + 1 >= waypoints.size) return 0.0
        var totalSlope = 0.0
        var totalWeight = 0.0
        var accDist = 0.0
        val maxSegs = min(PITCH_MAX_SEGMENTS, waypoints.size - idx - 1)
        for (i in 0 until maxSegs) {
            val segStart = waypoints[idx + i]
            val segEnd = waypoints[idx + i + 1]
            val sDx = segEnd.x - segStart.x
            val sDz = segEnd.z - segStart.z
            val sHDist = sqrt(sDx * sDx + sDz * sDz)
            if (sHDist < 0.01) continue
            accDist += sHDist
            if (accDist > PITCH_MAX_DISTANCE) break
            val slope = (segEnd.y - segStart.y) / sHDist
            val w = sHDist * (1.0 - (accDist / PITCH_MAX_DISTANCE))
            totalSlope += slope * w
            totalWeight += w
        }
        if (totalWeight <= 0.01) return 0.0
        val hLookDist = sqrt(lookDx * lookDx + lookDz * lookDz)
        return ((totalSlope / totalWeight) * min(hLookDist, PITCH_HORIZONTAL_CAP) * PITCH_SLOPE_SCALE)
            .coerceIn(-PITCH_CLAMP, PITCH_CLAMP)
    }

    private fun terrainNeedsPrejump(
        player: LocalPlayer,
        dx: Double,
        dz: Double,
        horizontalDist: Double,
        jumpProfile: JumpProfile
    ): Boolean {
        if (!player.onGround() || horizontalDist < 0.3) return false
        val nx = dx / horizontalDist
        val nz = dz / horizontalDist
        val feetY = player.y
        val cursor = BlockPos.MutableBlockPos()
        for (probeDist in PROBE_DISTANCES) {
            val px = player.x + nx * probeDist
            val pz = player.z + nz * probeDist
            val low = floor(feetY + PROBE_DEPTH_BELOW).toInt()
            val high = floor(feetY + BlockCache.PLAYER_HEIGHT).toInt()
            for (by in low..high) {
                cursor.set(floor(px).toInt(), by, floor(pz).toInt())
                val shape = BlockCache.getCollisionShape(cursor)
                if (shape.isEmpty) continue
                val top = by + shape.max(Direction.Axis.Y)
                val bottom = by + shape.min(Direction.Axis.Y)
                val stepHeight = top - feetY
                val landable = top <= feetY + jumpProfile.maxClimb && BlockCache.isBodyClearAt(px, top, pz)
                if (stepHeight > jumpProfile.stepHeight + STEP_JUMP_MARGIN && landable && top > feetY + GROUND_TOLERANCE) return true
                if (bottom > feetY + GROUND_TOLERANCE &&
                    bottom < feetY + BlockCache.PLAYER_HEIGHT &&
                    top > feetY + jumpProfile.stepHeight + STEP_JUMP_MARGIN &&
                    landable
                ) return true
            }
        }
        return false
    }

    private fun ledgeNeedsBridge(
        player: LocalPlayer,
        dx: Double,
        dz: Double,
        horizontalDist: Double,
        jumpProfile: JumpProfile
    ): Boolean {
        if (!player.onGround() || horizontalDist < 0.8) return false
        val nx = dx / horizontalDist
        val nz = dz / horizontalDist
        val feetY = player.y
        val farX = player.x + nx * LEDGE_PROBE_FAR
        val farZ = player.z + nz * LEDGE_PROBE_FAR
        val farTop = surfaceTopNear(farX, farZ, feetY, true, jumpProfile) ?: return false
        if (farTop <= feetY + jumpProfile.stepHeight + STEP_JUMP_MARGIN || farTop > feetY + jumpProfile.maxClimb) return false
        if (!BlockCache.isBodyClearAt(farX, farTop, farZ)) return false
        val midTop = surfaceTopNear(player.x + nx * LEDGE_PROBE_NEAR, player.z + nz * LEDGE_PROBE_NEAR, feetY, false, jumpProfile)
        return midTop == null || midTop < feetY - GROUND_TOLERANCE
    }

    private fun surfaceTopNear(
        x: Double,
        z: Double,
        feetY: Double,
        allowJumpHigh: Boolean,
        jumpProfile: JumpProfile
    ): Double? {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val maxOffset = if (allowJumpHigh) ceil(jumpProfile.maxClimb).toInt() else 0
        val cursor = BlockPos.MutableBlockPos()
        for (yOff in maxOffset downTo -2) {
            val by = floor(feetY).toInt() + yOff
            cursor.set(bx, by, bz)
            val shape = BlockCache.getCollisionShape(cursor)
            if (shape.isEmpty) continue
            val top = by + shape.max(Direction.Axis.Y)
            val limit = if (allowJumpHigh) jumpProfile.maxClimb else STEP_UP_MIN_HEIGHT
            if (top in (feetY - 1.5)..(feetY + limit)) return top
        }
        return null
    }
}
