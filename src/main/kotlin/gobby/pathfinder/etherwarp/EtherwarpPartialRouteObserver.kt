package gobby.pathfinder.etherwarp

internal class EtherwarpPartialRouteObserver(
    private val continueRoute: () -> Unit,
    private val maxContinuations: Int = MAX_CONTINUATIONS
) : EtherwarpExecutionObserver {
    private var continuations = 0

    override fun onProgress(completedHops: Int) = Unit

    override fun onTerminated(termination: EtherwarpExecutionTermination, executionNanos: Long) {
        if (termination != EtherwarpExecutionTermination.RAPID_PARTIAL || continuations >= maxContinuations) return
        continuations++
        continueRoute()
    }

    private companion object { const val MAX_CONTINUATIONS = 6 }
}
