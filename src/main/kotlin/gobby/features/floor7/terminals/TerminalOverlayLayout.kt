package gobby.features.floor7.terminals

internal enum class TerminalType {
    NUMBERS, COLORS, STARTS_WITH, RED_GREEN, RUBIX, MELODY
}

internal data class TerminalGridConfig(val cols: Int, val rows: Int, val rowStart: Int, val colStart: Int)

internal object TerminalOverlayLayout {
    fun gridConfig(type: TerminalType): TerminalGridConfig = when (type) {
        TerminalType.NUMBERS -> TerminalGridConfig(7, 2, 1, 1)
        TerminalType.COLORS -> TerminalGridConfig(7, 4, 1, 1)
        TerminalType.STARTS_WITH -> TerminalGridConfig(7, 3, 1, 1)
        TerminalType.RED_GREEN -> TerminalGridConfig(5, 3, 1, 2)
        TerminalType.RUBIX -> TerminalGridConfig(3, 3, 1, 3)
        TerminalType.MELODY -> TerminalGridConfig(7, 5, 0, 1)
    }

    fun compactToSlot(type: TerminalType, compactX: Int, compactY: Int): Int {
        val config = gridConfig(type)
        val chestRow = compactY + config.rowStart
        val chestColumn = if (type == TerminalType.MELODY && compactX == MELODY_BUTTON_COMPACT_COLUMN) {
            MELODY_BUTTON_CHEST_COLUMN
        } else compactX + config.colStart
        return chestRow * CHEST_ROW_WIDTH + chestColumn
    }

    fun slotToCompact(type: TerminalType, slot: Int): Pair<Int, Int>? {
        val config = gridConfig(type)
        val chestRow = slot / CHEST_ROW_WIDTH
        val chestColumn = slot % CHEST_ROW_WIDTH
        val compactY = chestRow - config.rowStart
        if (compactY !in 0 until config.rows) return null
        if (type == TerminalType.MELODY) {
            val compactX = if (chestColumn == MELODY_BUTTON_CHEST_COLUMN) MELODY_BUTTON_COMPACT_COLUMN else chestColumn - config.colStart
            if (compactX < 0 || compactX > MELODY_BUTTON_COMPACT_COLUMN || compactX == MELODY_GAP_COMPACT_COLUMN) return null
            return compactX to compactY
        }
        val compactX = chestColumn - config.colStart
        return compactX.takeIf { it in 0 until config.cols }?.let { it to compactY }
    }

    private const val CHEST_ROW_WIDTH = 9
    private const val MELODY_BUTTON_CHEST_COLUMN = 7
    private const val MELODY_BUTTON_COMPACT_COLUMN = 6
    private const val MELODY_GAP_COMPACT_COLUMN = 5
}
