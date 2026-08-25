package gobby.pathfinder.search

object HybridSearchPolicy {
    const val FAST_PATH_TIMEOUT_MS = 25L

    fun forTimeout(requestedMillis: Long): HybridSearchBudget {
        val reliabilityMillis = requestedMillis.coerceAtLeast(MIN_TIMEOUT_MS)
        return HybridSearchBudget(minOf(FAST_PATH_TIMEOUT_MS, reliabilityMillis), reliabilityMillis)
    }

    private const val MIN_TIMEOUT_MS = 1L
}

data class HybridSearchBudget(val fastMillis: Long, val reliabilityMillis: Long)
