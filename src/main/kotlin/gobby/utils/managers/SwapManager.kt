package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketSentEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.skyblockID
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket

enum class SwapResult {
    SUCCESS, ALREADY_HELD, TOO_FAST, NOT_FOUND, FAILED
}

/**
 * Some parts of this file are based on Quoi and the work of pigeonlover1998 under GNU General Public License v3.0.
 * @author pigeonlover1998 (https://github.com/pigeonlover1998)
 * License: https://github.com/pigeonlover1998/quoi/blob/main/LICENSE
 * Original source: https://github.com/pigeonlover1998/quoi/blob/9b79cde9db7992b231c3649185dc3a0cdb3f68c4/src/main/kotlin/quoi/utils/skyblock/player/SwapManager.kt
 */
object SwapManager {

    private const val COOLDOWN_TICKS = 1

    private var serverSlot = -1
    private var cooldown = 0
    var canUseAbility = true
        private set

    fun swap(slot: Int): SwapResult {
        val player = mc.player ?: return SwapResult.FAILED
        if (slot !in 0..8) return SwapResult.FAILED
        if (player.inventory.selectedSlot == slot) return SwapResult.ALREADY_HELD
        if (cooldown > 0) {
            errorMessage("Zero-tick swap blocked in swap()")
            return SwapResult.TOO_FAST
        }
        cooldown = COOLDOWN_TICKS
        canUseAbility = false
        player.inventory.selectedSlot = slot
        return SwapResult.SUCCESS
    }

    fun swapToSkyblockID(vararg ids: String): SwapResult {
        val player = mc.player ?: return SwapResult.FAILED
        if (player.mainHandStack.skyblockID in ids) return SwapResult.ALREADY_HELD
        val slot = (0..8).firstOrNull { player.inventory.getStack(it).skyblockID in ids }
            ?: return SwapResult.NOT_FOUND
        return swap(slot)
    }

    @SubscribeEvent()
    fun onTick(event: ClientTickEvent.Pre) {
        if (cooldown > 0) {
            cooldown--
            if (cooldown == 0) canUseAbility = true
        }
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        if (event.packet !is UpdateSelectedSlotC2SPacket) return
        val slot = event.packet.selectedSlot

        if (slot == serverSlot) {
            event.cancel()
            return
        }

        if (cooldown > 0 && serverSlot != -1) {
            event.cancel()
            errorMessage("Zero-tick swap blocked in onPacketSent")
            mc.player?.inventory?.selectedSlot = serverSlot
            return
        }

        serverSlot = slot
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        serverSlot = -1
        cooldown = 0
        canUseAbility = true
    }
}
