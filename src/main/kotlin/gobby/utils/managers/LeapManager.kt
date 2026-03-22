package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldUnloadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.skyblock.dungeon.DungeonListener
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.screen.sync.ItemStackHash
import net.minecraft.util.Formatting

object LeapManager {

    private const val TIMEOUT_TICKS = 40

    enum class State { IDLE, SWAPPING, OPENING_MENU, MENU_OPENED, LEAPING }

    var state = State.IDLE
        private set
    var leapTarget: String? = null
        private set
    private var windowId = -1
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
            rightClick()
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
            rightClick()
        }

        if (++ticks > TIMEOUT_TICKS) reset()
    }

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (state == State.IDLE) return

        when (val packet = event.packet) {
            is OpenScreenS2CPacket -> {
                if (state != State.OPENING_MENU) return
                if (!packet.name.string.contains("Spirit Leap")) return
                windowId = packet.syncId
                state = State.MENU_OPENED
                event.cancel()
            }

            is ScreenHandlerSlotUpdateS2CPacket -> {
                if (state != State.MENU_OPENED) return
                if (packet.syncId != windowId) return
                val slot = packet.slot
                if (slot !in 10..18) return
                val stack = packet.stack
                if (stack.isEmpty) return
                val itemName = Formatting.strip(stack.name.string) ?: return
                if (!itemName.equals(leapTarget, ignoreCase = true)) return

                state = State.LEAPING
                mc.networkHandler?.sendPacket(
                    ClickSlotC2SPacket(windowId, 0, slot.toShort(), 0.toByte(), SlotActionType.PICKUP, Int2ObjectOpenHashMap<ItemStackHash>(), ItemStackHash.EMPTY)
                )
                mc.networkHandler?.sendPacket(CloseHandledScreenC2SPacket(windowId))
                modMessage("§aAuto leaped to $leapTarget!")
                reset()
            }
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldUnloadEvent) {
        reset()
    }

    private fun reset() {
        leapTarget = null
        windowId = -1
        ticks = 0
        state = State.IDLE
    }
}
