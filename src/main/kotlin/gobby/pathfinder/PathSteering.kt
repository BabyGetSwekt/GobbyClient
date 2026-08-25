package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.utils.rotation.AngleUtils
import gobby.utils.rotation.RotationUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

internal class PathSteering {
    private var desiredYaw: Float = Float.NaN
    private var desiredPitch: Float = Float.NaN
    private val groundSteering = GroundPathSteering()

    fun reset() {
        desiredYaw = Float.NaN
        desiredPitch = Float.NaN
        groundSteering.reset()
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

        if (PathHeadObstacle.isBlocked(pos, dx, dz, horizontalDist)) {
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
        groundSteering.steer(waypoints, idx, pos, player, enableSpeedAdaptation, enableSprint)
        desiredYaw = groundSteering.desiredYaw
        desiredPitch = groundSteering.desiredPitch
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

}
