package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.PlayerUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Queues runnables by tick phase and executes them at the right moment.
 * Ensures item swaps happen before use-item packets in the same tick.
 */
object PacketOrderManager {

    enum class Phase {
        START,           // Beginning of tick (swap slots, mutate rotation)
        ITEM_USE,        // HEAD of sendMovementPackets — before vanilla's rotation send
        AFTER_MOVEMENT,  // TAIL of sendMovementPackets — after vanilla's rotation send
        ATTACK           // After item use (attack packets)
    }

    private val queues = ConcurrentHashMap<Phase, MutableList<Runnable>>()

    fun register(phase: Phase, action: Runnable) {
        queues.getOrPut(phase) { mutableListOf() }.add(action)
    }

    fun queueUseItem(yaw: Float, pitch: Float, canRun: () -> Boolean = { true }) =
        register(Phase.ITEM_USE) { if (canRun()) PlayerUtils.useItem(yaw, pitch) }

    fun queueUseItem() = register(Phase.ITEM_USE) { mc.player?.let { PlayerUtils.useItem(it.yRot, it.xRot) } }

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
