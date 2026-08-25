package gobby.pathfinder.etherwarp

class SearchDeadline(private val budgetMs: Long, private val parentDeadlineNanos: Long? = null) {
    private val startedNanos = System.nanoTime()
    private val deadlineNanos = minOf(
        safeDeadline(startedNanos, budgetMs),
        parentDeadlineNanos ?: Long.MAX_VALUE
    )

    val elapsed: Long get() = ((System.nanoTime() - startedNanos) / NANOS_PER_MILLISECOND).coerceAtLeast(0L)
    val remainingNanos: Long get() = (deadlineNanos - System.nanoTime()).coerceAtLeast(0L)
    val remainingMillis: Long get() = remainingNanos / NANOS_PER_MILLISECOND
    val expired: Boolean get() = System.nanoTime() >= deadlineNanos

    fun child(maxBudgetMs: Long): SearchDeadline = SearchDeadline(maxBudgetMs, deadlineNanos)

    fun slice(share: Double, capMillis: Long = Long.MAX_VALUE): SearchDeadline =
        child(minOf(capMillis, (remainingMillis * share).toLong()))

    private fun safeDeadline(start: Long, budget: Long): Long = try {
        Math.addExact(start, Math.multiplyExact(budget.coerceAtLeast(0L), NANOS_PER_MILLISECOND))
    } catch (_: ArithmeticException) {
        Long.MAX_VALUE
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
