package gobby.features.floor7.terminals

import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.skyblock.dungeon.TerminalUtils
import gobby.utils.timer.Clock
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.Items

object MelodyTerminal : TerminalSolver() {

    private const val ROW_WIDTH = 9
    private const val BUTTON_COL = 7
    private const val FIRST_CONTENT_SLOT = 9
    private const val PANE_OFFSET = 1
    private const val FIRST_EDGE = 0
    private const val LAST_EDGE = 4
    private const val LAST_ROW = 4

    override val isEnabled get() = true
    override fun matchesTitle(title: String) = title.contains("Click the button on time!")

    private var lastClickedRow = -1
    private val skipQueue = mutableListOf<Int>()
    private val skipClock = Clock()

    private fun reset() {
        lastClickedRow = -1
        skipQueue.clear()
    }

    override fun onDeactivate() = reset()
    override fun onActivate(screen: GenericContainerScreen) = reset()

    override fun solve(screen: GenericContainerScreen): TerminalClick? = null

    @SubscribeEvent
    override fun onTick(event: ClientTickEvent.Post) {
        val screen = tickScreen() ?: return
        if (processSkipQueue(screen)) return

        val handler = screen.screenHandler

        val targetCol = (0 until ROW_WIDTH).firstOrNull {
            handler.slots[it].stack.item == Items.MAGENTA_STAINED_GLASS_PANE
        } ?: return

        for (slot in FIRST_CONTENT_SLOT until handler.slots.size) {
            if (handler.slots[slot].stack.item != Items.LIME_STAINED_GLASS_PANE) continue
            if (slot % ROW_WIDTH != targetCol) continue

            val row = slot / ROW_WIDTH
            if (row == lastClickedRow) return

            lastClickedRow = row
            TerminalUtils.clickSlotDirect(handler.syncId, row * ROW_WIDTH + BUTTON_COL)

            val melodyCol = targetCol - PANE_OFFSET
            val skipMode = AutoTerminals.melodySkip
            val shouldSkip = skipMode == 2 || (skipMode == 1 && (melodyCol == FIRST_EDGE || melodyCol == LAST_EDGE))

            if (shouldSkip) {
                skipClock.update()
                for (nextRow in (row + 1)..LAST_ROW) {
                    skipQueue.add(nextRow * ROW_WIDTH + BUTTON_COL)
                }
            }
            return
        }
    }

    private fun processSkipQueue(screen: GenericContainerScreen): Boolean {
        if (skipQueue.isEmpty()) return false
        if (skipClock.hasTimePassed(AutoTerminals.melodySkipDelay.toLong())) {
            TerminalUtils.clickSlotDirect(screen.screenHandler.syncId, skipQueue.removeFirst())
            skipClock.update()
        }
        return true
    }
}
