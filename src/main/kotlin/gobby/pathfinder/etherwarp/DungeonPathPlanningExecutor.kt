package gobby.pathfinder.etherwarp

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicLong

object DungeonPathPlanningExecutor {
    private val requestSequence = AtomicLong()
    private val worker = Executors.newFixedThreadPool(PLANNER_THREADS, plannerThreadFactory())
    private var active: Future<*>? = null

    @Synchronized
    fun submit(task: (Long) -> Unit): Long {
        cancel()
        val requestId = requestSequence.incrementAndGet()
        active = worker.submit { task(requestId) }
        return requestId
    }

    @Synchronized
    fun preload() {
        if (active?.isDone == false) return
        active = worker.submit { PrimitiveEtherwarpSearch.preload() }
    }

    fun isCurrent(requestId: Long): Boolean = requestSequence.get() == requestId

    @Synchronized
    fun cancel() {
        requestSequence.incrementAndGet()
        active?.cancel(true)
        active = null
        EtherwarpPathExecutor.endPlanningSneak()
    }

    private fun plannerThreadFactory(): ThreadFactory = ThreadFactory { task ->
        Thread(task, "gobby-dungeon-planner").apply { isDaemon = true }
    }

    private const val PLANNER_THREADS = 1
}
