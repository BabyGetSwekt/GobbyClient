package gobby.utils.managers

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PlayerUtils
import gobby.utils.skyblock.dungeon.DungeonListener
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.screen.ScreenHandler
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
    private var container: ScreenHandler? = null
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
            PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
                val p = mc.player ?: return@register
                PlayerUtils.useItem(p.yaw, p.pitch)
            }
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
            PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE) {
                val p = mc.player ?: return@register
                PlayerUtils.useItem(p.yaw, p.pitch)
            }
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
                if (packet.syncId < 1 || packet.syncId > 100) return
                val player = mc.player ?: return
                container = packet.screenHandlerType.create(packet.syncId, player.inventory)
                state = State.MENU_OPENED
                event.cancel()
            }

            is ScreenHandlerSlotUpdateS2CPacket -> {
                if (state != State.MENU_OPENED) return
                val handler = container ?: return
                if (packet.syncId != handler.syncId) return
                val slot = packet.slot
                if (slot < 11) return

                handler.setStackInSlot(slot, packet.revision, packet.stack)

                if (slot > 16) {
                    modMessage("§cFailed to find leap target!")
                    close()
                    return
                }

                val stack = packet.stack
                if (stack.item != Items.PLAYER_HEAD) return
                val itemName = Formatting.strip(stack.name.string) ?: return
                if (!itemName.equals(leapTarget, ignoreCase = true)) return

                state = State.LEAPING
                sendWindowClick(slot, mc.player ?: return, handler)
                modMessage("§e[Leap] Sent a packet to slot $slot")
                modMessage("§aAuto leaped to $leapTarget!")
                reset()
            }
        }
    }

    private fun sendWindowClick(slotNumber: Int, player: PlayerEntity, handler: ScreenHandler) {
        val connection = mc.networkHandler ?: return
        val slots = handler.slots
        val before = slots.map { it.stack.copy() }

        handler.onSlotClick(slotNumber, 0, SlotActionType.CLONE, player)

        val changed = Int2ObjectOpenHashMap<ItemStackHash>()
        for (i in before.indices) {
            if (!ItemStack.areEqual(before[i], slots[i].stack)) {
                changed.put(i, ItemStackHash.fromItemStack(slots[i].stack, connection.componentHasher))
            }
        }

        val cursorHash = ItemStackHash.fromItemStack(handler.cursorStack, connection.componentHasher)
        connection.sendPacket(
            ClickSlotC2SPacket(
                handler.syncId,
                handler.revision,
                slotNumber.toShort(),
                0.toByte(),
                SlotActionType.CLONE,
                changed,
                cursorHash
            )
        )
        connection.sendPacket(CloseHandledScreenC2SPacket(handler.syncId))
    }

    private fun close() {
        val handler = container ?: return
        mc.networkHandler?.sendPacket(CloseHandledScreenC2SPacket(handler.syncId))
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
