package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.ChatFormatting

object StartsWithTerminal : TerminalSolver() {

    private val titleRegex = Regex("What starts with: \\W(\\w)\\W")

    override val isEnabled get() = true
    override fun matchesTitle(title: String) = titleRegex.containsMatchIn(ChatFormatting.stripFormatting(title) ?: "")

    override fun solve(screen: ContainerScreen): TerminalClick? {
        val strippedTitle = ChatFormatting.stripFormatting(screen.title.string) ?: return null
        val letter = titleRegex.find(strippedTitle)?.groupValues?.get(1)?.lowercase() ?: return null

        val slot = TerminalUtils.STARTS_WITH_SLOTS.firstOrNull { s ->
            val stack = screen.menu.slots[s].item
            if (stack.isEmpty || TerminalUtils.isItemDone(s, stack)) return@firstOrNull false
            val name = ChatFormatting.stripFormatting(stack.hoverName.string)?.trim()?.lowercase()
            !name.isNullOrEmpty() && name.startsWith(letter)
        } ?: return null
        return TerminalClick(slot)
    }
}
