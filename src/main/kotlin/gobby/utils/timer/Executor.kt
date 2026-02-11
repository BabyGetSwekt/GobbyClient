package gobby.utils.timer

import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList

typealias Executable = Executor.() -> Unit

object Executor {
    private val tasks = CopyOnWriteArrayList<ScheduledTask>()
    private val scope = CoroutineScope(Dispatchers.Default)

    fun execute(ticks: Int, repeat: Boolean = false, executable: Executable): ScheduledTask {
        return schedule(ticks, repeat, executable)
    }

    fun schedule(ticks: Int, repeat: Boolean = false, executable: Executable): ScheduledTask {
        val task = ScheduledTask(ticks, repeat, executable)
        tasks.add(task)
        return task
    }

    fun cancel(task: ScheduledTask) {
        tasks.remove(task)
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {

        tasks.toList().forEach { task ->
            task.tickCount++
            if (task.tickCount >= task.ticks) {
                scope.launch {
                    task.executable(this@Executor)
                }
                task.tickCount = 0
                if (!task.repeat) {
                    tasks.remove(task)
                }
            }
        }
    }

    data class ScheduledTask(
        val ticks: Int,
        val repeat: Boolean,
        val executable: Executable
    ) {
        var tickCount: Int = 0
    }

    /**
     * Usage:
     *  val task = Executor.schedule(10) {
     * println("Hello")
     * }
     *
     * // Schedule a one-time task to print "One-time Hello" after 10 ticks
     * Executor.schedule(10, repeat = false) {
     *  println("One-time Hello")
     * }
     *
     * // Cancel the repeating task if needed
     *  Executor.cancel(task)
     * }
     */
}
