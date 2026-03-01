package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.Items

object NumbersTerminal : TerminalSolver() {

    override val isEnabled get() = true
    override fun matchesTitle(title: String) = title.contains("Click in order!")

    override fun solve(screen: GenericContainerScreen): TerminalClick? {
        val slot = TerminalUtils.NUMBERS_SLOTS.filter { s ->
            val stack = screen.screenHandler.slots[s].stack
            !stack.isEmpty && !TerminalUtils.isTerminalItemDone(stack) && stack.item == Items.RED_STAINED_GLASS_PANE
        }.minByOrNull { screen.screenHandler.slots[it].stack.count } ?: return null
        return TerminalClick(slot)
    }
}
