package gobby.features.floor7.terminals

import gobby.utils.ChatUtils.modMessage
import gobby.utils.getItemID
import gobby.utils.getLoreStrings
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

    override fun onStuck(screen: ContainerScreen) {
        val letter = titleRegex.find(ChatFormatting.stripFormatting(screen.title.string) ?: "")
            ?.groupValues?.get(1)?.lowercase() ?: return

        fun nameOf(slot: Int) = ChatFormatting.stripFormatting(screen.menu.slots[slot].item.hoverName.string)?.trim() ?: "?"

        val slots = TerminalUtils.STARTS_WITH_SLOTS.filter { !screen.menu.slots[it].item.isEmpty }
        val (done, notDone) = slots.partition { TerminalUtils.isItemDone(it, screen.menu.slots[it].item) }

        modMessage(
            ":StartsWith Item Name: ${letter.uppercase()} | " +
                "All items {${slots.joinToString(", ") { nameOf(it) }}} | " +
                "StartsWith Finished Clicking {${done.joinToString(", ") { nameOf(it) }}} | " +
                "Items left Unclicked {${notDone.joinToString(", ") { nameOf(it) }}} |"
        )

        slots.filter { nameOf(it).lowercase().startsWith(letter) }
            .map { slot ->
                val stack = screen.menu.slots[slot].item
                "§7  slot=$slot §f'${nameOf(slot)}' §7glint=${TerminalUtils.isTerminalItemDone(stack)} " +
                    "id=${stack.getItemID()} x${stack.count} §8lore: ${stack.getLoreStrings().joinToString(" | ")}"
            }
            .forEach { modMessage(it) }
    }
}
