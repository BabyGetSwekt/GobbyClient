package gobby.utils.rotation

import gobby.utils.PlayerUtils.getEyePosition
import net.minecraft.util.math.Vec3d
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.sqrt

object AngleUtils {

    fun calcAimAngles(target: Vec3d): Pair<Float, Float>? = calcAimAngles(target.x, target.y, target.z)

    fun calcAimAngles(targetX: Double, targetY: Double, targetZ: Double): Pair<Float, Float>? {
        val eye = getEyePosition() ?: return null

        val dx = targetX - eye.x
        val dy = targetY - eye.y
        val dz = targetZ - eye.z

        val distXZ = sqrt(dx * dx + dz * dz)
        val yaw = -atan2(dx, dz) * 180.0 / Math.PI
        val pitch = -atan(dy / distXZ) * 180.0 / Math.PI

        if (pitch < -90 || pitch > 90 || yaw.isNaN() || pitch.isNaN()) return null
        return Pair(yaw.toFloat(), pitch.toFloat())
    }
}
