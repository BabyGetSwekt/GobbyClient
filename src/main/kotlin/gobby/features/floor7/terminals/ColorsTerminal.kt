package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.ChatFormatting

object ColorsTerminal : TerminalSolver() {

    private val titleRegex = Regex("Select all the ([\\w ]+) items!")

    override val isEnabled get() = true
    override fun matchesTitle(title: String) = titleRegex.containsMatchIn(ChatFormatting.stripFormatting(title) ?: "")

    override fun solve(screen: ContainerScreen): TerminalClick? {
        val strippedTitle = ChatFormatting.stripFormatting(screen.title.string) ?: return null
        val color = titleRegex.find(strippedTitle)?.groupValues?.get(1)?.lowercase()?.trim() ?: return null

        val slot = TerminalUtils.COLORS_SLOTS.firstOrNull { s ->
            val stack = screen.menu.slots[s].item
            if (stack.isEmpty || TerminalUtils.isTerminalItemDone(stack)) return@firstOrNull false
            val name = ChatFormatting.stripFormatting(stack.hoverName.string)?.lowercase()?.trim() ?: return@firstOrNull false
            TerminalUtils.normalizeItemName(name).startsWith(color)
        } ?: return null
        return TerminalClick(slot)
    }
}
