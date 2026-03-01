package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.util.Formatting

object ColorsTerminal : TerminalSolver() {

    private val titleRegex = Regex("Select all the ([\\w ]+) items!")

    override val isEnabled get() = true
    override fun matchesTitle(title: String) = titleRegex.containsMatchIn(Formatting.strip(title) ?: "")

    override fun solve(screen: GenericContainerScreen): TerminalClick? {
        val strippedTitle = Formatting.strip(screen.title.string) ?: return null
        val color = titleRegex.find(strippedTitle)?.groupValues?.get(1)?.lowercase()?.trim() ?: return null

        val slot = TerminalUtils.COLORS_SLOTS.firstOrNull { s ->
            val stack = screen.screenHandler.slots[s].stack
            if (stack.isEmpty || TerminalUtils.isTerminalItemDone(stack)) return@firstOrNull false
            val name = Formatting.strip(stack.name.string)?.lowercase()?.trim() ?: return@firstOrNull false
            TerminalUtils.normalizeItemName(name).startsWith(color)
        } ?: return null
        return TerminalClick(slot)
    }
}
