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
import net.minecraft.world.item.Items
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.ChatFormatting
import gobby.utils.ContainerClicks

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
            is ClientboundOpenScreenPacket -> handleOpenScreen(packet, event)
            is ClientboundContainerSetSlotPacket -> handleSlot(packet)
        }
    }

    private fun handleOpenScreen(packet: ClientboundOpenScreenPacket, event: PacketReceivedEvent) {
        if (state != State.OPENING_MENU || !packet.title.string.contains("Spirit Leap")) return
        if (packet.containerId !in 1..100) return
        val player = mc.player ?: return
        container = packet.type.create(packet.containerId, player.inventory)
        state = State.MENU_OPENED
        event.cancel()
    }

    private fun handleSlot(packet: ClientboundContainerSetSlotPacket) {
        if (state != State.MENU_OPENED) return
        val handler = container ?: return
        if (packet.containerId != handler.containerId || packet.slot < 11) return
        handler.setItem(packet.slot, packet.stateId, packet.item)
        if (packet.slot > 16) {
            modMessage("Failed to find leap target!")
            close()
            return
        }
        val itemName = ChatFormatting.stripFormatting(packet.item.hoverName.string) ?: return
        if (packet.item.item != Items.PLAYER_HEAD || !itemName.equals(leapTarget, ignoreCase = true)) return
        state = State.LEAPING
        ContainerClicks.clone(handler, packet.slot)
        ContainerClicks.close(handler.containerId)
        modMessage("[Leap] Sent a packet to slot " + packet.slot)
        modMessage("Auto leaped to " + leapTarget + "!")
        reset()
    }


    private fun close() {
        ContainerClicks.close(container?.containerId ?: return)
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

