package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.util.Formatting

object StartsWithTerminal : TerminalSolver() {

    private val titleRegex = Regex("What starts with: \\W(\\w)\\W")

    override val isEnabled get() = true
    override fun matchesTitle(title: String) = titleRegex.containsMatchIn(Formatting.strip(title) ?: "")

    override fun solve(screen: GenericContainerScreen): TerminalClick? {
        val strippedTitle = Formatting.strip(screen.title.string) ?: return null
        val letter = titleRegex.find(strippedTitle)?.groupValues?.get(1)?.lowercase() ?: return null

        val slot = TerminalUtils.STARTS_WITH_SLOTS.firstOrNull { s ->
            val stack = screen.screenHandler.slots[s].stack
            if (stack.isEmpty || TerminalUtils.isTerminalItemDone(stack)) return@firstOrNull false
            val name = Formatting.strip(stack.name.string)?.trim()?.lowercase()
            !name.isNullOrEmpty() && name.startsWith(letter)
        } ?: return null
        return TerminalClick(slot)
    }
}
