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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.world.item.ItemStack

abstract class ContainerSlotClicker(
    private val screenTitle: Regex,
    private val command: String
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

    protected fun clickOpenScreen(slot: Int): Boolean {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return false
        if (!screenTitle.containsMatchIn(screen.title.string)) return false
        val stack = screen.menu.getSlot(slot).item
        if (!shouldClick(stack)) {
            ContainerClicks.close(screen.menu.containerId)
            return true
        }
        sendClick(screen.menu.containerId, clickSlotFor(slot))
        return true
    }

    protected fun closeOpenScreen(): Boolean {
        val screen = mc.gui.screen() as? AbstractContainerScreen<*> ?: return false
        if (!screenTitle.containsMatchIn(screen.title.string)) return false
        ContainerClicks.close(screen.menu.containerId)
        return true
    }

    @SubscribeEvent
    fun onContainerPacket(event: PacketReceivedEvent) {
        if (suppressReopen(event)) return
        if (state == State.IDLE) return
        when (val packet = event.packet) {
            is ClientboundOpenScreenPacket -> if (acceptScreen(packet.title.string, packet.containerId)) event.cancel()
            is ClientboundContainerSetSlotPacket -> acceptSlot(packet.containerId, packet.slot, packet.item)
        }
    }

    private fun suppressReopen(event: PacketReceivedEvent): Boolean {
        if (suppressTicks <= 0 || state != State.IDLE) return false
        val packet = event.packet as? ClientboundOpenScreenPacket ?: return false
        if (!screenTitle.containsMatchIn(packet.title.string)) return false
        ContainerClicks.close(packet.containerId)
        event.cancel()
        return true
    }

    internal fun acceptScreen(title: String, containerId: Int): Boolean {
        if (!screenTitle.containsMatchIn(title)) {
            abandon()
            return false
        }
        if (state != State.WAITING_SCREEN) return false
        syncId = containerId
        state = State.WAITING_SLOT
        return true
    }

    internal fun acceptSlot(containerId: Int, slot: Int, stack: ItemStack) {
        if (state != State.WAITING_SLOT || containerId != syncId || slot != targetSlot) return
        if (shouldClick(stack)) clickAndClose(clickSlotFor(targetSlot)) else cancelFlow()
    }

    protected open fun shouldClick(stack: ItemStack): Boolean = true

    private fun clickAndClose(slot: Int) {
        sendClick(syncId, slot)
        finish()
    }

    private fun cancelFlow() {
        ContainerClicks.close(syncId)
        finish()
    }

    private fun finish() {
        currentAction = null
        attempts = 0
        reset()
        suppressTicks = 40
    }

    private fun abandon() {
        val retry = currentAction?.takeIf { attempts < 3 }
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
            if (++ticksWaiting > 60) abandon()
            return
        }
        if (mc.gui.screen() != null) {
            resumeDelay = getRandomInt(3, 7)
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
        if (state != State.IDLE) cooldownTicks = 1
        state = State.IDLE
        syncId = -1
        ticksWaiting = 0
    }
}
