package gobby.utils.managers

import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Queues runnables by tick phase and executes them at the right moment.
 * Ensures item swaps happen before use-item packets in the same tick.
 */
object PacketOrderManager {

    enum class Phase {
        START,      // Beginning of tick (swap slots)
        ITEM_USE,   // After swap (send use-item packets)
        ATTACK      // After item use (attack packets)
    }

    private val queues = ConcurrentHashMap<Phase, MutableList<Runnable>>()

    fun register(phase: Phase, action: Runnable) {
        queues.getOrPut(phase) { mutableListOf() }.add(action)
    }

    fun execute(phase: Phase) {
        val list = queues[phase] ?: return
        synchronized(list) {
            if (list.isNotEmpty()) {
                list.forEach { it.run() }
                list.clear()
            }
        }
    }

    @SubscribeEvent
    fun onTickPre(event: ClientTickEvent.Pre) {
        execute(Phase.START)
    }

    @SubscribeEvent
    fun onTickPost(event: ClientTickEvent.Post) {
        execute(Phase.ATTACK)
    }
}
