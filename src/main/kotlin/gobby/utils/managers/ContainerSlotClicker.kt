package gobby.utils.managers

import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils
import gobby.utils.ContainerClicks
import gobby.utils.LocationUtils
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket

abstract class ContainerSlotClicker(
    private val screenTitle: String,
    private val command: String,
    private val abandonAboveSlot: Int = NO_ABANDON_SLOT
) {
    private enum class State { IDLE, WAITING_SCREEN, WAITING_SLOT }

    private var state = State.IDLE
    private var targetSlot = 0
    private var syncId = -1
    private var ticksWaiting = 0
    private var cooldownTicks = 0
    private var pendingAction: (() -> Unit)? = null

    val isBusy: Boolean get() = state != State.IDLE

    protected open fun clickSlotFor(targetSlot: Int): Int = targetSlot

    protected fun request(action: () -> Unit) {
        if (isBusy || cooldownTicks > 0) {
            pendingAction = action
            return
        }
        action()
    }

    protected fun openFor(slot: Int) {
        if (!canOpen()) return
        targetSlot = slot
        state = State.WAITING_SCREEN
        ticksWaiting = 0
        sendOpenCommand(command)
    }

    protected open fun canOpen(): Boolean = LocationUtils.onSkyblock

    protected open fun sendOpenCommand(command: String) = ChatUtils.sendCommand(command)

    protected open fun sendClick(syncId: Int, slot: Int) {
        ContainerClicks.pickup(syncId, slot)
        ContainerClicks.close(syncId)
    }

    @SubscribeEvent
    fun onContainerPacket(event: PacketReceivedEvent) {
        if (state == State.IDLE) return
        when (val packet = event.packet) {
            is ClientboundOpenScreenPacket -> if (acceptScreen(packet.title.string, packet.containerId)) event.cancel()
            is ClientboundContainerSetSlotPacket -> acceptSlot(packet.containerId, packet.slot)
        }
    }

    internal fun acceptScreen(title: String, containerId: Int): Boolean {
        if (state != State.WAITING_SCREEN) return false
        if (!title.contains(screenTitle)) {
            reset()
            return false
        }
        syncId = containerId
        state = State.WAITING_SLOT
        return true
    }

    internal fun acceptSlot(containerId: Int, slot: Int) {
        if (state != State.WAITING_SLOT || containerId != syncId) return
        if (slot == targetSlot) return clickAndClose(clickSlotFor(targetSlot))
        if (abandonAboveSlot != NO_ABANDON_SLOT && slot > abandonAboveSlot) reset()
    }

    private fun clickAndClose(slot: Int) {
        sendClick(syncId, slot)
        reset()
    }

    @SubscribeEvent
    fun onContainerTick(event: ClientTickEvent.Post) {
        if (cooldownTicks > 0) {
            if (--cooldownTicks == 0) runPending()
            return
        }
        if (state == State.IDLE) return
        if (++ticksWaiting > TIMEOUT_TICKS) reset()
    }

    private fun runPending() {
        val action = pendingAction ?: return
        pendingAction = null
        action()
    }

    @SubscribeEvent
    fun onContainerWorldLoad(event: WorldLoadEvent) {
        reset()
        pendingAction = null
    }

    private fun reset() {
        if (state != State.IDLE) cooldownTicks = COOLDOWN_TICKS
        state = State.IDLE
        syncId = -1
        ticksWaiting = 0
    }

    protected companion object {
        const val NO_ABANDON_SLOT = -1
        private const val TIMEOUT_TICKS = 60
        private const val COOLDOWN_TICKS = 1
    }
}
