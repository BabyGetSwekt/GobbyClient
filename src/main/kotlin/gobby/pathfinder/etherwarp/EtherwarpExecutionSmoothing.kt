package gobby.pathfinder.etherwarp

import net.minecraft.world.phys.Vec3

internal class EtherwarpExecutionSmoothing {
    private var transition: Transition? = null

    fun clear() {
        transition = null
    }

    fun capture(before: Vec3, actual: Vec3, route: List<EtherwarpNode>, enabled: Boolean, nowNanos: Long): Boolean {
        if (!enabled || route.none { matches(it.eye, actual) }) return false
        transition = Transition(before, actual, nowNanos)
        return true
    }

    fun position(actual: Vec3, nowNanos: Long): Vec3? {
        val current = transition ?: return null
        val progress = ((nowNanos - current.startedNanos).toDouble() / DURATION_NANOS).coerceIn(0.0, 1.0)
        if (progress >= 1.0) {
            transition = null
            return null
        }
        val eased = 1.0 - (1.0 - progress) * (1.0 - progress)
        return actual.add(current.from.lerp(current.to, eased).subtract(current.to))
    }

    private fun matches(expected: Vec3, actual: Vec3): Boolean {
        val delta = actual.subtract(expected)
        return delta.horizontalDistanceSqr() < LANDING_TOLERANCE_SQ && kotlin.math.abs(delta.y) <= LANDING_HEIGHT_TOLERANCE
    }

    private data class Transition(val from: Vec3, val to: Vec3, val startedNanos: Long)

    private companion object {
        const val DURATION_NANOS = 50_000_000L
    }
}
