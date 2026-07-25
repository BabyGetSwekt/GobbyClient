package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.LocationUtils
import gobby.utils.skyblockID
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.network.HashedStack

object EquipmentManager {

    private const val SCREEN_TITLE = "Stats & Equipment"
    private const val HELMET_SLOT = 11
    private const val CHESTPLATE_SLOT = 20
    private const val LEGGINGS_SLOT = 29
    private const val BOOTS_SLOT = 38
    private const val HOTBAR_CONTAINER_START = 81
    private const val MAIN_INV_CONTAINER_START = 54
    private const val TIMEOUT_TICKS = 60
    private const val COOLDOWN_TICKS = 1

    private enum class State { IDLE, WAITING_SCREEN, WAITING_SLOT }

    private var state = State.IDLE
    val isSwapping: Boolean get() = state != State.IDLE
    private var targetSlot = 0
    private var itemSlot = -1
    private var syncId = -1
    private var ticksWaiting = 0
    private var cooldownTicks = 0
    private var pendingAction: (() -> Unit)? = null

    fun swapHead(vararg skyblockIds: String) = swap(HELMET_SLOT, *skyblockIds)
    fun swapChestplate(vararg skyblockIds: String) = swap(CHESTPLATE_SLOT, *skyblockIds)
    fun swapLeggings(vararg skyblockIds: String) = swap(LEGGINGS_SLOT, *skyblockIds)
    fun swapBoots(vararg skyblockIds: String) = swap(BOOTS_SLOT, *skyblockIds)

    private fun swap(equipSlot: Int, vararg skyblockIds: String) {
        val action = { startSwap(equipSlot, *skyblockIds) }
        if (state != State.IDLE || cooldownTicks > 0) {
            pendingAction = action
            return
        }
        action()
    }

    private fun startSwap(equipSlot: Int, vararg skyblockIds: String) {
        if (!LocationUtils.onSkyblock) return
        val slot = findItemInInventory(*skyblockIds)
        if (slot == -1) {
            errorMessage("Item not found in inventory")
            return
        }
        targetSlot = equipSlot
        itemSlot = slot
        state = State.WAITING_SCREEN
        ticksWaiting = 0
        ChatUtils.sendCommand("stats")
    }

    private fun invToContainerSlot(invSlot: Int): Int {
        return if (invSlot < 9) HOTBAR_CONTAINER_START + invSlot else MAIN_INV_CONTAINER_START + (invSlot - 9)
    }

    private fun findItemInInventory(vararg skyblockIds: String): Int {
        val player = mc.player ?: return -1
        val inv = player.inventory
        for (i in 0..35) {
            val stack = inv.getItem(i)
            if (stack.skyblockID in skyblockIds) return i
        }
        return -1
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (state == State.IDLE) return
        when (val packet = event.packet) {
            is ClientboundOpenScreenPacket -> {
                if (state != State.WAITING_SCREEN) return
                if (!packet.title.string.contains(SCREEN_TITLE)) { reset(); return }
                syncId = packet.containerId
                state = State.WAITING_SLOT
                event.cancel()
            }

            is ClientboundContainerSetSlotPacket -> {
                if (state != State.WAITING_SLOT) return
                if (packet.containerId != syncId) return
                val slot = packet.slot
                if (slot == targetSlot) {
                    val containerSlot = invToContainerSlot(itemSlot)
                    mc.connection?.send(
                        ServerboundContainerClickPacket(syncId, 0, containerSlot.toShort(), 0.toByte(), ContainerInput.PICKUP, Int2ObjectOpenHashMap<HashedStack>(), HashedStack.EMPTY)
                    )
                    mc.connection?.send(ServerboundContainerClosePacket(syncId))
                    reset()
                }
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (cooldownTicks > 0) {
            cooldownTicks--
            if (cooldownTicks == 0 && pendingAction != null) {
                val action = pendingAction
                pendingAction = null
                action?.invoke()
            }
            return
        }
        if (state == State.IDLE) return
        if (++ticksWaiting > TIMEOUT_TICKS) reset()
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        reset()
        pendingAction = null
    }

    private fun reset() {
        if (state != State.IDLE) cooldownTicks = COOLDOWN_TICKS
        state = State.IDLE
        syncId = -1
        ticksWaiting = 0
    }
}
