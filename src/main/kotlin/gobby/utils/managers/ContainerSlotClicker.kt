package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils
import gobby.utils.ContainerClicks
import gobby.utils.LocationUtils
import gobby.utils.Utils.getRandomInt
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket

abstract class ContainerSlotClicker(
    private val screenTitle: String,
    private val command: String,
    private val abandonAboveSlot: Int = NO_ABANDON_SLOT
) : SilentContainer {
    private enum class State { IDLE, WAITING_SCREEN, WAITING_SLOT }

    private var state = State.IDLE
    private var targetSlot = 0
    private var syncId = -1
    private var ticksWaiting = 0
    private var cooldownTicks = 0
    private var pendingAction: (() -> Unit)? = null
    private var currentAction: (() -> Unit)? = null
    private var attempts = 0
    private var suppressTicks = 0
    private var resumeDelay = 0

    val isBusy: Boolean get() = state != State.IDLE

    override val isRunning: Boolean get() = isBusy

    override fun yieldToScreen() {
        if (syncId != -1) ContainerClicks.close(syncId)
        abandon()
    }

    init {
        SilentContainerFlow.register(this)
    }

    protected open fun clickSlotFor(targetSlot: Int): Int = targetSlot

    protected fun request(action: () -> Unit) {
        if (isBusy || cooldownTicks > 0 || mc.gui.screen() != null) {
            pendingAction = action
            return
        }
        start(action)
    }

    private fun start(action: () -> Unit) {
        currentAction = action
        attempts++
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
        if (suppressReopen(event)) return
        if (state == State.IDLE) return
        when (val packet = event.packet) {
            is ClientboundOpenScreenPacket -> if (acceptScreen(packet.title.string, packet.containerId)) event.cancel()
            is ClientboundContainerSetSlotPacket -> acceptSlot(packet.containerId, packet.slot)
        }
    }

    private fun suppressReopen(event: PacketReceivedEvent): Boolean {
        if (suppressTicks <= 0) return false
        val packet = event.packet as? ClientboundOpenScreenPacket ?: return false
        if (!packet.title.string.contains(screenTitle)) return false
        suppressTicks = 0
        ContainerClicks.close(packet.containerId)
        event.cancel()
        return true
    }

    internal fun acceptScreen(title: String, containerId: Int): Boolean {
        if (!title.contains(screenTitle)) {
            abandon()
            return false
        }
        if (state != State.WAITING_SCREEN) return false
        syncId = containerId
        state = State.WAITING_SLOT
        return true
    }

    internal fun acceptSlot(containerId: Int, slot: Int) {
        if (state != State.WAITING_SLOT || containerId != syncId) return
        if (slot == targetSlot) return clickAndClose(clickSlotFor(targetSlot))
        if (abandonAboveSlot != NO_ABANDON_SLOT && slot > abandonAboveSlot) abandon()
    }

    private fun clickAndClose(slot: Int) {
        sendClick(syncId, slot)
        currentAction = null
        attempts = 0
        reset()
        suppressTicks = REOPEN_SUPPRESS_TICKS
    }

    private fun abandon() {
        val retry = currentAction?.takeIf { attempts < MAX_ATTEMPTS }
        reset()
        pendingAction = retry
    }

    @SubscribeEvent
    fun onContainerTick(event: ClientTickEvent.Post) {
        if (suppressTicks > 0) suppressTicks--
        if (cooldownTicks > 0) {
            cooldownTicks--
            return
        }
        if (state != State.IDLE) {
            if (++ticksWaiting > TIMEOUT_TICKS) abandon()
            return
        }
        if (mc.gui.screen() != null) {
            resumeDelay = getRandomInt(MIN_RESUME_DELAY, MAX_RESUME_DELAY)
            return
        }
        if (resumeDelay > 0) {
            resumeDelay--
            return
        }
        runPending()
    }

    private fun runPending() {
        val action = pendingAction ?: return
        pendingAction = null
        start(action)
    }

    @SubscribeEvent
    fun onContainerWorldLoad(event: WorldLoadEvent) {
        reset()
        pendingAction = null
        currentAction = null
        attempts = 0
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
        private const val REOPEN_SUPPRESS_TICKS = 40
        private const val MAX_ATTEMPTS = 3
        private const val MIN_RESUME_DELAY = 3
        private const val MAX_RESUME_DELAY = 7
    }
}
