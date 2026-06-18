package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.events.PacketReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.features.floor7.terminals.AutoTerminals
import gobby.features.floor7.terminals.TerminalClick
import gobby.utils.timer.Clock
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket
import net.minecraft.world.inventory.ContainerInput

object TerminalUtils {

    private val clickClock = Clock()
    private var isFirstClick = true
    private var clickedWindow = false
    private val clickedSlots = mutableSetOf<Int>()
    private var currentWindowId = -1
    val solution = mutableListOf<TerminalClick>()

    val NUMBERS_SLOTS = intArrayOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25)
    val COLORS_SLOTS = intArrayOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    )
    val STARTS_WITH_SLOTS = intArrayOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    )
    val RUBIX_SLOTS = intArrayOf(12, 13, 14, 21, 22, 23, 30, 31, 32)
    val RED_GREEN_SLOTS = intArrayOf(11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33)

    private val COLOR_NORMALIZATIONS = arrayOf(
        "light gray" to "silver", "wool" to "white", "bone" to "white",
        "ink" to "black", "lapis" to "blue", "cocoa" to "brown",
        "dandelion" to "yellow", "rose" to "red", "cactus" to "green"
    )

    fun normalizeItemName(name: String): String {
        val (prefix, replacement) = COLOR_NORMALIZATIONS.firstOrNull { name.startsWith(it.first) } ?: return name
        return replacement + name.removePrefix(prefix)
    }

    fun isGuardFailed(): Boolean = !AutoTerminals.enabled || (!DungeonUtils.inP3 && !AutoTerminals.notP3)

    fun onTerminalOpen(screen: ContainerScreen) {
        clickClock.update()
        isFirstClick = true
        clickedWindow = false
        clickedSlots.clear()
        currentWindowId = screen.menu.containerId
        solution.clear()
    }

    @SubscribeEvent
    fun onPacketReceived(event: PacketReceivedEvent) {
        if (currentWindowId == -1) return

        if (event.packet is ClientboundOpenScreenPacket) {
            clickedWindow = false
            clickedSlots.clear()
        } else if (event.packet is ClientboundContainerSetSlotPacket && clickedWindow) {
            val pkt = event.packet as ClientboundContainerSetSlotPacket
            if (pkt.containerId == currentWindowId) {
                clickedWindow = false
                clickedSlots.clear()
            }
        }
    }

    /**
     * Called every tick by TerminalSolver.
     * Solver provides a fresh solution each tick — we only click
     * the first one that we haven't already clicked this window.
     */
    fun tryClick(screen: ContainerScreen, slot: Int, button: Int = 2): Boolean {
        if (clickedWindow) {
            // Break threshold: if server never re-opens window
            if (clickClock.hasTimePassed(AutoTerminals.breakThreshold.toLong())) {
                clickedWindow = false
                clickedSlots.clear()
            } else {
                return false
            }
        }

        val delay = if (isFirstClick) AutoTerminals.firstDelay.toLong() else AutoTerminals.clickDelay.toLong()
        if (!clickClock.hasTimePassed(delay)) return false

        clickSlot(screen.menu.containerId, slot, button)
        if (button == 2) clickedSlots.add(slot)
        clickedWindow = true
        return true
    }

    fun clickSlot(syncId: Int, slotId: Int, button: Int = 2) {
        val action = if (button == 2) ContainerInput.CLONE else ContainerInput.PICKUP
        val player = mc.player ?: return
        mc.gameMode?.handleContainerInput(syncId, slotId, button, action, player)
        clickClock.update()
        isFirstClick = false
    }

    fun clickSlotDirect(syncId: Int, slotId: Int) {
        val player = mc.player ?: return
        mc.gameMode?.handleContainerInput(syncId, slotId, 2, ContainerInput.CLONE, player)
    }

    fun isItemDone(slot: Int, stack: ItemStack): Boolean =
        slot in clickedSlots || isTerminalItemDone(stack)

    fun isTerminalItemDone(stack: ItemStack): Boolean =
        stack.componentsPatch.get(DataComponentMap.EMPTY, DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null
}
