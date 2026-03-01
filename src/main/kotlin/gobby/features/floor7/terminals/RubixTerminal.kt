package gobby.features.floor7.terminals

import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.Items

object RubixTerminal : TerminalSolver() {

    private val COLOR_ORDER = listOf(
        Items.RED_STAINED_GLASS_PANE,
        Items.ORANGE_STAINED_GLASS_PANE,
        Items.YELLOW_STAINED_GLASS_PANE,
        Items.GREEN_STAINED_GLASS_PANE,
        Items.BLUE_STAINED_GLASS_PANE
    )
    private val colorCount = COLOR_ORDER.size
    private val lastColorIndices = IntArray(9) { -1 }

    override val isEnabled get() = true
    override fun matchesTitle(title: String) = title.contains("Change all to same color!")

    override fun onDeactivate() {
        lastColorIndices.fill(-1)
    }

    private fun circularDistance(from: Int, to: Int): Int {
        val forward = ((to - from) % colorCount + colorCount) % colorCount
        return minOf(forward, colorCount - forward)
    }

    fun getFullSolution(screen: GenericContainerScreen): IntArray? {
        val colorIndices = IntArray(9) { i ->
            val live = COLOR_ORDER.indexOf(screen.screenHandler.slots[TerminalUtils.RUBIX_SLOTS[i]].stack.item)
            if (live != -1) {
                lastColorIndices[i] = live
                live
            } else {
                lastColorIndices[i]
            }
        }
        if (colorIndices.any { it == -1 }) return null

        val targetIdx = (0 until colorCount).minBy { target ->
            colorIndices.sumOf { circularDistance(it, target) }
        }

        return IntArray(9) { i ->
            val ci = colorIndices[i]
            if (ci == targetIdx) 0
            else {
                val fwd = ((targetIdx - ci) % colorCount + colorCount) % colorCount
                if (fwd <= colorCount - fwd) fwd else -(colorCount - fwd)
            }
        }
    }

    override fun solve(screen: GenericContainerScreen): TerminalClick? {
        val solution = getFullSolution(screen) ?: return null
        val i = solution.indexOfFirst { it != 0 }
        if (i == -1) return null
        return TerminalClick(TerminalUtils.RUBIX_SLOTS[i], if (solution[i] > 0) 0 else 1)
    }
}
