package gobby.features.floor7.terminals

import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.skyblock.dungeon.TerminalUtils
import gobby.utils.timer.Clock
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object MelodyTerminal : TerminalSolver() {

    private const val ROW_WIDTH = 9
    private const val BUTTON_COL = 7
    private const val PANE_OFFSET = 1
    private const val FIRST_EDGE = 0
    private const val LAST_EDGE = 4
    private const val FIRST_ROW = 1
    private const val LAST_ROW = 4
    private const val RETRY_MILLIS = 300L

    override val isEnabled get() = true

    override fun matchesTitle(title: String) = title.contains("Click the button on time!")

    private val rowClocks = Array(LAST_ROW + 1) { Clock() }
    private val rowClicked = BooleanArray(LAST_ROW + 1)
    private val skipQueue = mutableListOf<Int>()
    private val skipClock = Clock()

    private fun reset() {
        rowClicked.fill(false)
        skipQueue.clear()
    }

    override fun onDeactivate() = reset()

    override fun onActivate(screen: ContainerScreen) = reset()

    override fun solve(screen: ContainerScreen): TerminalClick? = null

    @SubscribeEvent
    override fun onTick(event: ClientTickEvent.Post) {
        val screen = tickScreen() ?: return
        if (processSkipQueue(screen)) return

        val slots = screen.menu.slots
        val targetCol = targetColumn(slots) ?: return
        val row = currentRow(slots, targetCol) ?: return
        if (laneColumn(slots, row) != targetCol) return
        if (rowClicked[row] && !rowClocks[row].hasTimePassed(RETRY_MILLIS)) return

        rowClicked[row] = true
        rowClocks[row].update()
        TerminalUtils.clickSlotDirect(screen.menu.containerId, row * ROW_WIDTH + BUTTON_COL)
        queueSkips(row, targetCol)
    }

    private fun targetColumn(slots: List<Slot>): Int? =
        (0 until ROW_WIDTH).firstOrNull { slots.getOrNull(it)?.item?.item == Items.STAINED_GLASS_PANE.magenta() }

    private fun currentRow(slots: List<Slot>, targetCol: Int): Int? =
        activeRow(slots) ?: (LAST_ROW downTo FIRST_ROW).firstOrNull { laneColumn(slots, it) == targetCol }

    private fun activeRow(slots: List<Slot>): Int? =
        (LAST_ROW downTo FIRST_ROW).firstOrNull { isActiveButton(slots.getOrNull(it * ROW_WIDTH + BUTTON_COL)?.item) }

    private fun isActiveButton(stack: ItemStack?): Boolean {
        val item = stack?.takeUnless(ItemStack::isEmpty)?.item ?: return false
        return item == Items.DYED_TERRACOTTA.lime() || item == Items.DYED_TERRACOTTA.green()
    }

    private fun laneColumn(slots: List<Slot>, row: Int): Int =
        (0 until ROW_WIDTH).firstOrNull {
            slots.getOrNull(row * ROW_WIDTH + it)?.item?.item == Items.STAINED_GLASS_PANE.lime()
        } ?: -1

    private fun queueSkips(row: Int, targetCol: Int) {
        val melodyCol = targetCol - PANE_OFFSET
        val skipMode = AutoTerminals.melodySkip
        if (skipMode != 2 && !(skipMode == 1 && (melodyCol == FIRST_EDGE || melodyCol == LAST_EDGE))) return
        skipClock.update()
        (row + 1..LAST_ROW).forEach { skipQueue.add(it * ROW_WIDTH + BUTTON_COL) }
    }

    private fun processSkipQueue(screen: ContainerScreen): Boolean {
        if (skipQueue.isEmpty()) return false
        if (skipClock.hasTimePassed(AutoTerminals.melodySkipDelay.toLong())) {
            TerminalUtils.clickSlotDirect(screen.menu.containerId, skipQueue.removeFirst())
            skipClock.update()
        }
        return true
    }
}
