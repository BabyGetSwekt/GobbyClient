package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.features.floor7.terminals.AutoTerminals
import gobby.utils.timer.Clock
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.SlotActionType

object TerminalUtils {

    private val clickClock = Clock()
    private var isFirstClick = true
    private var waitingForResponse = false
    private var lastSignature = 0

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

    fun onTerminalOpen(screen: GenericContainerScreen) {
        clickClock.update()
        isFirstClick = true
        waitingForResponse = false
        lastSignature = containerSignature(screen)
    }

    fun tryClick(screen: GenericContainerScreen, slot: Int, button: Int = 2): Boolean {
        val sig = containerSignature(screen)
        if (sig != lastSignature) {
            lastSignature = sig
            waitingForResponse = false
        }

        if (waitingForResponse) {
            val timeout = AutoTerminals.breakThreshold.toLong()
            if (timeout > 0 && clickClock.hasTimePassed(timeout)) {
                waitingForResponse = false
            } else {
                return false
            }
        }

        val delay = if (isFirstClick) AutoTerminals.firstDelay.toLong() else AutoTerminals.clickDelay.toLong()
        if (!clickClock.hasTimePassed(delay)) return false

        clickSlot(screen.screenHandler.syncId, slot, button)
        lastSignature = containerSignature(screen)
        return true
    }

    fun clickSlot(syncId: Int, slotId: Int, button: Int = 2) {
        val action = if (button == 2) SlotActionType.CLONE else SlotActionType.PICKUP
        mc.interactionManager?.clickSlot(syncId, slotId, button, action, mc.player)
        clickClock.update()
        isFirstClick = false
        waitingForResponse = true
    }

    fun clickSlotDirect(syncId: Int, slotId: Int) {
        mc.interactionManager?.clickSlot(syncId, slotId, 2, SlotActionType.CLONE, mc.player)
    }

    /**
     * Checks if a terminal item has been completed (clicked) by looking for the
     * ENCHANTMENT_GLINT_OVERRIDE component specifically. Unlike vanilla hasGlint(),
     * this ignores items that naturally have enchantments, only detecting the explicit
     * glint override that Hypixel adds to mark completed terminal items.
     */
    fun isTerminalItemDone(stack: ItemStack): Boolean =
        stack.componentChanges.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)?.isPresent == true

    private fun containerSignature(screen: GenericContainerScreen): Int =
        screen.screenHandler.slots.fold(0) { hash, slot ->
            val stack = slot.stack
            var h = hash * 31 + System.identityHashCode(stack.item)
            h = h * 31 + stack.count
            h * 31 + if (isTerminalItemDone(stack)) 1 else 0
        }
}
