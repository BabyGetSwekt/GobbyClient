package gobby.pathfinder.etherwarp

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object RayCone {

    const val SPHERE_DEG = 180.0
    const val OUTSIDE = -1.0
    private const val POLE_EPSILON = 1.0E-6

    fun yawHalfWidth(bandPitch: Float, targetPitch: Double, coneDeg: Double): Double {
        val band = Math.toRadians(bandPitch.toDouble())
        val target = Math.toRadians(targetPitch)
        val denominator = cos(band) * cos(target)
        if (abs(denominator) < POLE_EPSILON) return if (abs(bandPitch - targetPitch) <= coneDeg) SPHERE_DEG else OUTSIDE
        val cosDelta = (cos(Math.toRadians(coneDeg)) - sin(band) * sin(target)) / denominator
        return when {
            cosDelta <= -1.0 -> SPHERE_DEG
            cosDelta >= 1.0 -> OUTSIDE
            else -> Math.toDegrees(acos(cosDelta))
        }
    }

    fun slotOf(yaw: Double, yawStep: Float, slots: Int): Int =
        ((yaw / yawStep).roundToInt() % slots + slots) % slots

    fun spanOf(halfWidthDeg: Double, yawStep: Float, slots: Int): Int =
        if (halfWidthDeg >= SPHERE_DEG) slots else (halfWidthDeg / yawStep).toInt()

    fun contains(slot: Int, center: Int, span: Int, slots: Int): Boolean {
        if (span < 0) return false
        val delta = abs(slot - center)
        return minOf(delta, slots - delta) <= span
    }
}
