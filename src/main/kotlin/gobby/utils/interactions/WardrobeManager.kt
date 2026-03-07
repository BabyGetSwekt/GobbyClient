package gobby.utils.interactions

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldUnloadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.screen.sync.ItemStackHash

object WardrobeManager {

    private const val SLOT_OFFSET = 36
    private const val MAX_SLOT = 45
    private const val TIMEOUT_TICKS = 60

    private enum class State { IDLE, WAITING_SCREEN, WAITING_SLOT }

    private const val COOLDOWN_TICKS = 1

    private var state = State.IDLE
    private var targetSlot = 0
    private var syncId = -1
    private var ticksWaiting = 0
    private var cooldownTicks = 0
    private var pendingSlot = -1

    fun swap(wardrobeSlot: Int) {
        if (state != State.IDLE || cooldownTicks > 0) {
            pendingSlot = wardrobeSlot
            return
        }
        startSwap(wardrobeSlot)
    }

    private fun startSwap(wardrobeSlot: Int) {
        targetSlot = SLOT_OFFSET + wardrobeSlot - 1
        state = State.WAITING_SCREEN
        ticksWaiting = 0
        ChatUtils.sendCommand("wardrobe")
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        when (val packet = event.packet) {
            is OpenScreenS2CPacket -> {
                if (state != State.WAITING_SCREEN) return
                if (!packet.name.string.contains("Wardrobe")) { reset(); return }
                syncId = packet.syncId
                state = State.WAITING_SLOT
                event.cancel()
            }

            is ScreenHandlerSlotUpdateS2CPacket -> {
                if (state != State.WAITING_SLOT) return
                val slot = packet.slot
                if (slot == targetSlot) {
                    mc.networkHandler?.sendPacket(
                        ClickSlotC2SPacket(syncId, 0, slot.toShort(), 0.toByte(), SlotActionType.PICKUP, Int2ObjectOpenHashMap<ItemStackHash>(), ItemStackHash.EMPTY)
                    )
                    mc.networkHandler?.sendPacket(CloseHandledScreenC2SPacket(syncId))
                    reset()
                }
                if (slot > MAX_SLOT) reset()
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (cooldownTicks > 0) {
            cooldownTicks--
            if (cooldownTicks == 0 && pendingSlot != -1) {
                val slot = pendingSlot
                pendingSlot = -1
                startSwap(slot)
            }
            return
        }
        if (state == State.IDLE) return
        if (++ticksWaiting > TIMEOUT_TICKS) reset()
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldUnloadEvent) {
        reset()
        clearQueue()
    }

    private fun reset() {
        if (state != State.IDLE) cooldownTicks = COOLDOWN_TICKS
        state = State.IDLE
        syncId = -1
        ticksWaiting = 0
    }

    private fun clearQueue() {
        pendingSlot = -1
    }
}
