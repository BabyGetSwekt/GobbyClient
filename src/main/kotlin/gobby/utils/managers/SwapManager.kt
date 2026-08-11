package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.PacketSentEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.skyblockID
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket

enum class SwapResult {
    SUCCESS, ALREADY_HELD, TOO_FAST, NOT_FOUND, FAILED
}

/**
 * @author pigeonlover1998 (https://github.com/pigeonlover1998)
 * License: https://github.com/pigeonlover1998/quoi/blob/main/LICENSE
 * Original source: https://github.com/pigeonlover1998/quoi/blob/9b79cde9db7992b231c3649185dc3a0cdb3f68c4/src/main/kotlin/quoi/utils/skyblock/player/SwapManager.kt
 */
object SwapManager {

    private const val COOLDOWN_TICKS = 1
    private const val NO_SLOT = -1
    private val HOTBAR = 0..8

    private var serverSlot = NO_SLOT
    private var swappedThisTick = false
    private var cooldown = 0
    var canUseAbility = true
        private set
    val currentServerSlot: Int get() = serverSlot

    fun swap(slot: Int): SwapResult {
        val player = mc.player ?: return SwapResult.FAILED
        if (slot !in HOTBAR) return SwapResult.FAILED
        if (player.inventory.selectedSlot == slot) return SwapResult.ALREADY_HELD
        if (swappedThisTick) return SwapResult.TOO_FAST
        applySwap(slot)
        return SwapResult.SUCCESS
    }

    fun swapToSkyblockID(vararg ids: String): SwapResult {
        val player = mc.player ?: return SwapResult.FAILED
        if (player.mainHandItem.skyblockID in ids) return SwapResult.ALREADY_HELD
        val slot = HOTBAR.firstOrNull { player.inventory.getItem(it).skyblockID in ids } ?: return SwapResult.NOT_FOUND
        return swap(slot)
    }

    fun swapToItem(vararg ids: String): Int {
        val player = mc.player ?: return NO_SLOT
        if (player.mainHandItem.skyblockID in ids) return player.inventory.selectedSlot
        if (swappedThisTick) return NO_SLOT
        val slot = HOTBAR.firstOrNull { player.inventory.getItem(it).skyblockID in ids } ?: return NO_SLOT
        applySwap(slot)
        return slot
    }

    fun swapSlot(slot: Int): Boolean {
        val player = mc.player ?: return false
        if (player.inventory.selectedSlot == slot) return true
        if (swappedThisTick || slot !in HOTBAR) return false
        applySwap(slot)
        return true
    }

    private fun applySwap(slot: Int) {
        mc.player?.inventory?.selectedSlot = slot
        mc.connection?.send(ServerboundSetCarriedItemPacket(slot))
        cooldown = COOLDOWN_TICKS
        canUseAbility = false
    }

    enum class SendDecision { ACCEPT, CANCEL_REDUNDANT, CANCEL_ROLLBACK }

    internal fun decideSend(slot: Int, serverSlot: Int, swappedThisTick: Boolean): SendDecision = when {
        slot == serverSlot -> SendDecision.CANCEL_REDUNDANT
        swappedThisTick -> SendDecision.CANCEL_ROLLBACK
        else -> SendDecision.ACCEPT
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        swappedThisTick = false
        if (cooldown > 0 && --cooldown == 0) canUseAbility = true
    }

    @SubscribeEvent
    fun onPacketReceived(event: PacketReceivedEvent) {
        val packet = event.packet
        if (packet is ClientboundSetHeldSlotPacket) serverSlot = packet.slot()
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        val packet = event.packet
        if (packet !is ServerboundSetCarriedItemPacket) return
        when (decideSend(packet.slot, serverSlot, swappedThisTick)) {
            SendDecision.CANCEL_REDUNDANT -> event.cancel()
            SendDecision.CANCEL_ROLLBACK -> {
                event.cancel()
                if (serverSlot in HOTBAR) mc.player?.inventory?.selectedSlot = serverSlot
                modMessage("§c[SwapManager] Prevented zero-tick swap! slot=${packet.slot}, serverSlot=$serverSlot")
            }
            SendDecision.ACCEPT -> {
                serverSlot = packet.slot
                swappedThisTick = true
            }
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        serverSlot = NO_SLOT
        swappedThisTick = false
        cooldown = 0
        canUseAbility = true
    }
}
