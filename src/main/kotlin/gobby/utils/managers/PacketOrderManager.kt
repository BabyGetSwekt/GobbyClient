package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.PlayerUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.Collections

object PacketOrderManager {

    class Registration internal constructor(internal val phase: Phase, internal val action: Runnable, internal val generation: Long) {
        @Volatile var active = true
    }

    enum class Phase {
        START,
        ITEM_USE,
        AFTER_MOVEMENT,
        ATTACK
    }

    private val queues = ConcurrentHashMap<Phase, MutableList<Registration>>()
    private val generation = AtomicLong()

    fun register(phase: Phase, action: Runnable): Registration = Registration(phase, action, generation.get()).also {
        queues.computeIfAbsent(phase) { Collections.synchronizedList(mutableListOf()) }.add(it)
    }

    fun cancel(registration: Registration) {
        registration.active = false
        queues[registration.phase]?.let { synchronized(it) { it.remove(registration) } }
    }

    fun queueUseItem(yaw: Float, pitch: Float, canRun: () -> Boolean = { true }): Registration =
        register(Phase.ITEM_USE) { if (canRun()) PlayerUtils.useItem(yaw, pitch) }

    fun queueUseItem(): Registration = register(Phase.ITEM_USE) { mc.player?.let { PlayerUtils.useItem(it.yRot, it.xRot) } }

    fun execute(phase: Phase) {
        val list = queues[phase] ?: return
        val registrations = synchronized(list) { list.toList().also { list.clear() } }
        registrations.forEach { if (it.active && it.generation == generation.get()) it.action.run() }
    }

    fun clear() {
        generation.incrementAndGet()
        queues.values.forEach { list -> synchronized(list) { list.forEach { it.active = false }; list.clear() } }
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
