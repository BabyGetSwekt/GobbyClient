package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.Items

object NumbersTerminal : TerminalSolver() {

    override val isEnabled get() = true

    override fun matchesTitle(title: String) = title.contains("Click in order!")

    override fun solve(screen: ContainerScreen): TerminalClick? {
        val slot = TerminalUtils.NUMBERS_SLOTS.filter { s ->
            val stack = screen.menu.slots[s].item
            !stack.isEmpty && !TerminalUtils.isTerminalItemDone(stack) && stack.item == Items.STAINED_GLASS_PANE.red()
        }.minByOrNull { screen.menu.slots[it].item.count } ?: return null
        return TerminalClick(slot)
    }
}
