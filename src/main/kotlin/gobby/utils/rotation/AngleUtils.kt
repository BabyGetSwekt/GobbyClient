package gobby.utils.rotation

import gobby.utils.PlayerUtils.getEyePosition
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object AngleUtils {

    fun calcAimAngles(target: Vec3): Pair<Float, Float>? = calcAimAngles(target.x, target.y, target.z)

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

    fun calcAimAnglesBetween(from: Vec3, to: Vec3): Pair<Float, Float> =
        calcAimAnglesFromDelta(to.x - from.x, to.y - from.y, to.z - from.z)

    fun directionFromAngles(yaw: Float, pitch: Float): Vec3 {
        val y = Math.toRadians(yaw.toDouble())
        val p = Math.toRadians(pitch.toDouble())
        val cosP = cos(p)
        return Vec3(-cosP * sin(y), -sin(p), cosP * cos(y))
    }

    fun Direction.horizontalDegrees(): Float = when (this) {
        Direction.SOUTH -> 0f
        Direction.WEST -> 90f
        Direction.NORTH -> 180f
        Direction.EAST -> 270f
        else -> 0f
    }
}
