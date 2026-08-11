package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.skyblock.dungeon.DungeonListener
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.network.HashedStack
import net.minecraft.ChatFormatting

object LeapManager {

    private const val TIMEOUT_TICKS = 40

    enum class State { IDLE, SWAPPING, OPENING_MENU, MENU_OPENED, LEAPING }

    var state = State.IDLE
        private set
    var leapTarget: String? = null
        private set
    private var container: AbstractContainerMenu? = null
    private var ticks = 0

    fun scheduleLeap(name: String): Boolean {
        if (state != State.IDLE) return false
        return swapAndLeap { leapTarget = name }
    }

    fun scheduleLeap(dungeonClass: DungeonClass): Boolean {
        if (state != State.IDLE) return false
        DungeonListener.refreshTeammates()
        val teammate = DungeonUtils.dungeonTeammates.values
            .firstOrNull { it.dungeonClass == dungeonClass && it.name != mc.player?.name?.string }

        if (teammate == null) {
            modMessage("§cNo ${dungeonClass.name} found to leap to.")
            return false
        }

        return swapAndLeap { leapTarget = teammate.name }
    }

    private fun swapAndLeap(setTarget: () -> Unit): Boolean {
        val result = SwapManager.swapToSkyblockID(DungeonUtils.SPIRIT_LEAP, DungeonUtils.INFINILEAP)
        if (result == SwapResult.NOT_FOUND) {
            modMessage("§cNo Spirit Leap found in hotbar!")
            return false
        }

        setTarget()
        ticks = 0

        if (result == SwapResult.ALREADY_HELD) {
            state = State.OPENING_MENU
            PacketOrderManager.queueUseItem()
        } else {
            state = State.SWAPPING
        }

        return true
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (state == State.IDLE) return

        if (state == State.SWAPPING && SwapManager.canUseAbility) {
            state = State.OPENING_MENU
            PacketOrderManager.queueUseItem()
        }

        if (++ticks > TIMEOUT_TICKS) reset()
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (state == State.IDLE) return

        when (val packet = event.packet) {
            is ClientboundOpenScreenPacket -> {
                if (state != State.OPENING_MENU) return
                if (!packet.title.string.contains("Spirit Leap")) return
                if (packet.containerId < 1 || packet.containerId > 100) return
                val player = mc.player ?: return
                container = packet.type.create(packet.containerId, player.inventory)
                state = State.MENU_OPENED
                event.cancel()
            }

            is ClientboundContainerSetSlotPacket -> {
                if (state != State.MENU_OPENED) return
                val handler = container ?: return
                if (packet.containerId != handler.containerId) return
                val slot = packet.slot
                if (slot < 11) return

                handler.setItem(slot, packet.stateId, packet.item)

                if (slot > 16) {
                    modMessage("§cFailed to find leap target!")
                    close()
                    return
                }

                val stack = packet.item
                if (stack.item != Items.PLAYER_HEAD) return
                val itemName = ChatFormatting.stripFormatting(stack.hoverName.string) ?: return
                if (!itemName.equals(leapTarget, ignoreCase = true)) return

                state = State.LEAPING
                sendWindowClick(slot, mc.player ?: return, handler)
                modMessage("§e[Leap] Sent a packet to slot $slot")
                modMessage("§aAuto leaped to $leapTarget!")
                reset()
            }
        }
    }

    private fun sendWindowClick(slotNumber: Int, player: Player, handler: AbstractContainerMenu) {
        val connection = mc.connection ?: return
        val slots = handler.slots
        val before = slots.map { it.item.copy() }

        handler.clicked(slotNumber, 0, ContainerInput.CLONE, player)

        val changed = Int2ObjectOpenHashMap<HashedStack>()
        for (i in before.indices) {
            if (!ItemStack.matches(before[i], slots[i].item)) {
                changed.put(i, HashedStack.create(slots[i].item, connection.decoratedHashOpsGenenerator()))
            }
        }

        val cursorHash = HashedStack.create(handler.carried, connection.decoratedHashOpsGenenerator())
        connection.send(
            ServerboundContainerClickPacket(
                handler.containerId,
                handler.stateId,
                slotNumber.toShort(),
                0.toByte(),
                ContainerInput.CLONE,
                changed,
                cursorHash
            )
        )
        connection.send(ServerboundContainerClosePacket(handler.containerId))
    }

    private fun close() {
        val handler = container ?: return
        mc.connection?.send(ServerboundContainerClosePacket(handler.containerId))
        reset()
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        reset()
    }

    private fun reset() {
        leapTarget = null
        container = null
        ticks = 0
        state = State.IDLE
    }
}
