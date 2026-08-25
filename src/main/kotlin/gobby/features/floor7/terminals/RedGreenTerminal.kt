package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.Items

object RedGreenTerminal : TerminalSolver() {

    override val isEnabled get() = true

    override fun matchesTitle(title: String) = title.contains("Correct all the panes!")

    override fun solve(screen: ContainerScreen) =
        TerminalUtils.RED_GREEN_SLOTS.firstOrNull {
            screen.menu.slots[it].item.item == Items.STAINED_GLASS_PANE.red()
        }?.let { TerminalClick(it) }
}
