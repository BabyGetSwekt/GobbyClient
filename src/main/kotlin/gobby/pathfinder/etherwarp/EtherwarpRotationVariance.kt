package gobby.pathfinder.etherwarp

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import gobby.utils.rotation.CatmullRom
import kotlin.random.Random

internal object EtherwarpRotationVariance {
    private const val MAX_YAW = 5.75f
    private const val MAX_PITCH = 4.5f
    private const val YAW_DEVIATION = 2.8f
    private const val PITCH_DEVIATION = 2.1f
    private const val EDGES_PER_SEGMENT = 3.0
    private const val MAX_YAW_STEP = 2.4f
    private const val MAX_PITCH_STEP = 1.9f
    private const val ACCEPTED_HEADROOM = 0.8f
    private val VALIDATION_SCALES = floatArrayOf(1f, 0.82f, 0.66f, 0.5f, 0.36f, 0.24f, 0.14f, 0.08f)

    data class Offset(val yaw: Float, val pitch: Float) {
        companion object { val ZERO = Offset(0f, 0f) }
    }

    fun generate(edgeCount: Int, random: Random = Random.Default): List<Offset> {
        if (edgeCount <= 0) return emptyList()
        val segments = max(1, ceil(max(1, edgeCount - 1) / EDGES_PER_SEGMENT).toInt())
        val controls = List(segments + 3) {
            Offset(
                (gaussian(random) * YAW_DEVIATION).coerceIn(-MAX_YAW, MAX_YAW),
                (gaussian(random) * PITCH_DEVIATION).coerceIn(-MAX_PITCH, MAX_PITCH)
            )
        }
        var previous = Offset.ZERO
        return List(edgeCount) { index ->
            val progress = if (edgeCount == 1) 0.5 else index.toDouble() * segments / (edgeCount - 1)
            val segment = min(segments - 1, progress.toInt())
            val t = (progress - segment).toFloat().coerceIn(0f, 1f)
            val sampled = Offset(
                CatmullRom.interpolate(controls[segment].yaw, controls[segment + 1].yaw, controls[segment + 2].yaw, controls[segment + 3].yaw, t).coerceIn(-MAX_YAW, MAX_YAW),
                CatmullRom.interpolate(controls[segment].pitch, controls[segment + 1].pitch, controls[segment + 2].pitch, controls[segment + 3].pitch, t).coerceIn(-MAX_PITCH, MAX_PITCH)
            )
            val limited = if (index == 0) sampled else Offset(
                sampled.yaw.coerceIn(previous.yaw - MAX_YAW_STEP, previous.yaw + MAX_YAW_STEP),
                sampled.pitch.coerceIn(previous.pitch - MAX_PITCH_STEP, previous.pitch + MAX_PITCH_STEP)
            )
            previous = limited
            limited
        }
    }

    fun resolve(base: Aim, desired: Offset, enabled: Boolean, accepts: (Aim) -> Boolean): Aim {
        if (!enabled || desired == Offset.ZERO) return base
        for (scale in VALIDATION_SCALES) {
            val boundary = scaled(base, desired, scale)
            if (boundary == base || !accepts(boundary)) continue
            val candidate = scaled(base, desired, scale * ACCEPTED_HEADROOM)
            if (candidate != base && accepts(candidate)) return candidate
        }
        return base
    }

    private fun scaled(base: Aim, offset: Offset, scale: Float) = Aim(base.yaw + offset.yaw * scale, (base.pitch + offset.pitch * scale).coerceIn(-90f, 90f))

    private fun gaussian(random: Random): Float {
        val first = random.nextDouble().coerceAtLeast(1.0E-12)
        return (sqrt(-2.0 * ln(first)) * cos(2.0 * Math.PI * random.nextDouble())).toFloat()
    }
}
