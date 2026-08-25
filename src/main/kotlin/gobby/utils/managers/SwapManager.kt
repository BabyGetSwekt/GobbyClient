package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.PacketSentEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.mixinterface.IInteractionManagerAccessor
import gobby.utils.findEtherwarpableHotbarSlot
import gobby.utils.isEtherwarpable
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
        return applySwap(slot).takeIf { it }?.let { SwapResult.SUCCESS } ?: SwapResult.FAILED
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
        return slot.takeIf { applySwap(it) } ?: NO_SLOT
    }

    fun swapToEtherwarpableItem(): Int {
        val player = mc.player ?: return NO_SLOT
        if (player.mainHandItem.isEtherwarpable()) return player.inventory.selectedSlot
        if (swappedThisTick) return NO_SLOT
        val slot = findEtherwarpableHotbarSlot()
        if (slot < 0) return NO_SLOT
        return slot.takeIf { applySwap(it) } ?: NO_SLOT
    }

    fun swapSlot(slot: Int): Boolean {
        val player = mc.player ?: return false
        if (player.inventory.selectedSlot == slot) return true
        if (swappedThisTick || slot !in HOTBAR) return false
        return applySwap(slot)
    }

    private fun applySwap(slot: Int): Boolean {
        val player = mc.player ?: return false
        val gameMode = mc.gameMode as? IInteractionManagerAccessor ?: return false
        player.inventory.selectedSlot = slot
        gameMode.`gobbyclient$syncSelectedSlot`()
        cooldown = COOLDOWN_TICKS
        canUseAbility = false
        return true
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
        serverSlot = packet.slot
        swappedThisTick = true
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        serverSlot = NO_SLOT
        swappedThisTick = false
        cooldown = 0
        canUseAbility = true
    }
}
