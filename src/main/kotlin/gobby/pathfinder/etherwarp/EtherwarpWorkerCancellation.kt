package gobby.pathfinder.etherwarp

internal object EtherwarpWorkerCancellation {
    fun cancel(ctx: EtherwarpContext, workers: List<java.util.concurrent.Future<*>>, timedOut: Boolean) {
        if (timedOut && ctx.result == null) ctx.timedOut = true
        ctx.solved = true
        workers.forEach { it.cancel(true) }
    }
}
