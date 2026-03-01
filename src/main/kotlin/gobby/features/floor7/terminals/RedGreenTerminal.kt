package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.Items

object RedGreenTerminal : TerminalSolver() {

    override val isEnabled get() = true
    override fun matchesTitle(title: String) = title.contains("Correct all the panes!")

    override fun solve(screen: GenericContainerScreen) =
        TerminalUtils.RED_GREEN_SLOTS.firstOrNull {
            screen.screenHandler.slots[it].stack.item == Items.RED_STAINED_GLASS_PANE
        }?.let { TerminalClick(it) }
}
