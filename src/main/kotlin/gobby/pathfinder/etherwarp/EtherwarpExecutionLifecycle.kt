package gobby.pathfinder.etherwarp

enum class EtherwarpExecutionTermination { ARRIVED, RAPID_PARTIAL, FAILED, CANCELLED }

interface EtherwarpExecutionObserver {
    fun onProgress(completedHops: Int)

    fun onTerminated(termination: EtherwarpExecutionTermination, executionNanos: Long)
}

internal class EtherwarpExecutionLifecycle {
    private var observer: EtherwarpExecutionObserver? = null
    private var startedNanos = 0L
    private var reportedHops = 0
    private var terminated = false
    private var deferredTermination: (() -> Unit)? = null

    fun start(nextObserver: EtherwarpExecutionObserver?) {
        observer = nextObserver
        startedNanos = System.nanoTime()
        reportedHops = 0
        terminated = false
    }

    fun progress(completedHops: Int) {
        val resolved = completedHops.coerceAtLeast(0)
        if (terminated || resolved <= reportedHops) return
        reportedHops = resolved
        observer?.onProgress(resolved)
    }

    fun terminate(reason: EtherwarpExecutionTermination, deferObserver: Boolean = false) {
        if (terminated) return
        terminated = true
        val elapsed = (System.nanoTime() - startedNanos).coerceAtLeast(0L)
        val callback = observer?.let { current -> { current.onTerminated(reason, elapsed) } }
        observer = null
        if (deferObserver) deferredTermination = callback else callback?.invoke()
    }

    fun flushDeferredTermination() {
        val callback = deferredTermination ?: return
        deferredTermination = null
        callback()
    }

    fun resetForReuse() {
        observer = null
        startedNanos = 0L
        reportedHops = 0
        terminated = false
    }

}
