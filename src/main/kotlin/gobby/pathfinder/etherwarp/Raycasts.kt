package gobby.pathfinder.etherwarp

import gobby.utils.rotation.AngleUtils.directionFromAngles
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max

class Raycasts(
    val dx: DoubleArray,
    val dy: DoubleArray,
    val dz: DoubleArray,
    val yaws: FloatArray,
    val pitches: FloatArray,
    val scale: Double,
    val bandStart: IntArray,
    val bandSize: IntArray,
    val bandYawStep: FloatArray,
    val bandPitch: FloatArray
) {
    val size: Int get() = dx.size
    val bandCount: Int get() = bandStart.size

    companion object {
        private const val MIN_PITCH = -90f
        private const val MAX_PITCH = 90f
        private const val FULL_TURN = 360f
        private const val MIN_YAW_COS = 0.01f

        fun generate(pitchStep: Float, yawStep: Float, scale: Double): Raycasts {
            val bandPitch = pitches(pitchStep)
            val bandYawStep = FloatArray(bandPitch.size) { yawStepAt(bandPitch[it], yawStep) }
            val bandSize = IntArray(bandPitch.size) { ceil(FULL_TURN / bandYawStep[it]).toInt() }
            val bandStart = IntArray(bandPitch.size)
            var total = 0
            bandSize.forEachIndexed { band, count ->
                bandStart[band] = total
                total += count
            }
            val dx = DoubleArray(total)
            val dy = DoubleArray(total)
            val dz = DoubleArray(total)
            val yaws = FloatArray(total)
            val pitches = FloatArray(total)
            bandPitch.indices.forEach { band ->
                repeat(bandSize[band]) { slot ->
                    val index = bandStart[band] + slot
                    val yaw = slot * bandYawStep[band]
                    val look = directionFromAngles(yaw, bandPitch[band])
                    dx[index] = look.x * scale
                    dy[index] = look.y * scale
                    dz[index] = look.z * scale
                    yaws[index] = yaw
                    pitches[index] = bandPitch[band]
                }
            }
            return Raycasts(dx, dy, dz, yaws, pitches, scale, bandStart, bandSize, bandYawStep, bandPitch)
        }

        private fun pitches(step: Float): FloatArray {
            val count = ceil((MAX_PITCH - MIN_PITCH) / step).toInt() + 1
            return FloatArray(count) { (MIN_PITCH + it * step).coerceAtMost(MAX_PITCH) }
        }

        private fun yawStepAt(pitch: Float, step: Float): Float =
            step / max(MIN_YAW_COS, cos(Math.toRadians(pitch.toDouble())).toFloat())
    }
}
