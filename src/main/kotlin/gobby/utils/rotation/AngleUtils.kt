package gobby.utils.rotation

import gobby.utils.PlayerUtils.getEyePosition
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.math.sqrt

object AngleUtils {

    fun calcAimAngles(target: Vec3d): Pair<Float, Float>? = calcAimAngles(target.x, target.y, target.z)

    fun calcAimAngles(targetX: Double, targetY: Double, targetZ: Double): Pair<Float, Float>? {
        val eye = getEyePosition() ?: return null
        val (yaw, pitch) = calcAimAnglesFromDelta(targetX - eye.x, targetY - eye.y, targetZ - eye.z)
        if (yaw.isNaN() || pitch.isNaN() || pitch < -90f || pitch > 90f) return null
        return yaw to pitch
    }

    fun calcAimAnglesFromDelta(dx: Double, dy: Double, dz: Double): Pair<Float, Float> {
        val horizontalDist = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizontalDist))).toFloat()
        return yaw to pitch
    }

    fun calcAimAnglesBetween(from: Vec3d, to: Vec3d): Pair<Float, Float> =
        calcAimAnglesFromDelta(to.x - from.x, to.y - from.y, to.z - from.z)

    fun Direction.horizontalDegrees(): Float = when (this) {
        Direction.SOUTH -> 0f
        Direction.WEST -> 90f
        Direction.NORTH -> 180f
        Direction.EAST -> 270f
        else -> 0f
    }
}
