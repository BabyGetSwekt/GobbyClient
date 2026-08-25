package gobby.pathfinder.etherwarp

import net.minecraft.world.phys.Vec3

internal class EtherwarpExecutionAuthoritative(
    private val route: () -> List<EtherwarpNode>,
    private val inFlight: () -> ArrayDeque<EtherwarpExecutionHop>,
    private val onLanding: (Int, Vec3) -> Unit
) {
    private val smoothing = EtherwarpExecutionSmoothing()

    fun clear() = smoothing.clear()

    fun shouldCapture(): Boolean = EtherwarpExecutionSettings.teleportSmoothingEnabled && route().isNotEmpty()

    fun observe(before: Vec3, actual: Vec3, nowNanos: Long) {
        if (before.distanceToSqr(actual) >= TELEPORT_MIN_SQ) {
            val hops = inFlight()
            val matched = furthestMatchingHopIndex(actual, hops)
            if (matched != null) {
                repeat(matched) { hops.removeFirst() }
                hops.removeFirstOrNull()?.let {
                    EtherwarpExecutionReporter.logLanding(it, actual)
                    onLanding(it.label, actual)
                }
            } else {
                furthestMatchingRouteIndex(actual, route())?.takeIf { it > 0 }?.let { onLanding(it, actual) }
            }
            val visualStart = position(before, nowNanos) ?: before
            smoothing.capture(visualStart, actual, route(), shouldCapture(), nowNanos)
        }
    }

    fun position(actual: Vec3, nowNanos: Long): Vec3? = smoothing.position(actual, nowNanos)

}

internal fun furthestMatchingHopIndex(actual: Vec3, hops: ArrayDeque<EtherwarpExecutionHop>): Int? =
    hops.indices.reversed().firstOrNull { EtherwarpExecutionReporter.isExpected(actual, hops[it]) }

internal fun furthestMatchingRouteIndex(actual: Vec3, route: List<EtherwarpNode>): Int? =
    route.indices.reversed().firstOrNull { EtherwarpExecutionReporter.isExpected(actual, route[it].eye) }
