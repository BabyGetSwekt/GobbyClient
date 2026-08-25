package gobby.gui.click

private const val GENERAL_SECTION = "GENERAL"
private const val STRING_EXTRA_H = 16
private const val COLUMN_COUNT = 2

internal data class PanelFrame(val x: Int, val y: Int)

internal data class PlacedRow(val setting: Setting<*>, val x: Int, val y: Int, val w: Int, val h: Int)

internal data class PlacedBlock(
    val title: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val cardY: Int,
    val cardH: Int,
    val rows: List<PlacedRow>
)

internal object SettingsLayout {

    fun rowHeight(setting: Setting<*>): Int =
        if (setting is StringSetting) SETTINGS_ROW_H + STRING_EXTRA_H else SETTINGS_ROW_H

    fun contentLeft(frame: PanelFrame): Int = frame.x + SIDEBAR_W_SETTINGS + SETTINGS_SIDE_PAD

    fun contentWidth(): Int = PANEL_W - SIDEBAR_W_SETTINGS - SETTINGS_SIDE_PAD * 2

    fun contentTop(frame: PanelFrame): Int = frame.y + SETTINGS_HEADER_H + SETTINGS_SECTION_GAP

    fun contentBottom(frame: PanelFrame): Int = frame.y + PANEL_H - SETTINGS_SIDE_PAD / 2

    fun columnWidth(): Int = (contentWidth() - SETTINGS_COLUMN_GAP) / COLUMN_COUNT

    fun build(frame: PanelFrame, mod: Module): List<PlacedBlock> {
        val columnW = columnWidth()
        val left = contentLeft(frame)
        val top = contentTop(frame)
        val columnX = listOf(left, left + columnW + SETTINGS_COLUMN_GAP)
        val columnY = IntArray(COLUMN_COUNT) { top }
        val placed = groups(mod).map { group ->
            val target = columnY.indices.minBy { columnY[it] }
            place(group, columnX[target], columnY[target], columnW).also { columnY[target] = it.bottom() }
        }
        if (!SettingsPreview.appliesTo(mod)) return placed
        val target = columnY.indices.minBy { columnY[it] }
        return placed + previewBlock(columnX[target], columnY[target], columnW)
    }

    fun contentHeight(frame: PanelFrame, blocks: List<PlacedBlock>): Int =
        (blocks.maxOfOrNull { it.bottom() } ?: contentTop(frame)) - contentTop(frame)

    private fun PlacedBlock.bottom(): Int = cardY + cardH + SETTINGS_SECTION_GAP

    private fun previewBlock(x: Int, y: Int, w: Int) = PlacedBlock(
        SettingsPreview.SECTION_TITLE, x, y, w, y + SETTINGS_SECTION_H, SettingsPreview.cardHeight(), emptyList()
    )

    private fun place(group: Pair<String, List<Setting<*>>>, x: Int, y: Int, w: Int): PlacedBlock {
        val cardY = y + SETTINGS_SECTION_H
        var rowY = cardY + SETTINGS_CARD_PAD
        val rows = group.second.map { setting ->
            val h = rowHeight(setting)
            PlacedRow(setting, x + SETTINGS_CARD_PAD, rowY, w - SETTINGS_CARD_PAD * 2, h).also { rowY += h }
        }
        return PlacedBlock(group.first, x, y, w, cardY, rowY - cardY + SETTINGS_CARD_PAD, rows)
    }

    private fun groups(mod: Module): List<Pair<String, List<Setting<*>>>> {
        val result = mutableListOf<Pair<String, List<Setting<*>>>>()
        val loose = mutableListOf<Setting<*>>()
        topLevel(mod).forEach { setting ->
            if (setting !is DropDownSetting) {
                loose += setting
                return@forEach
            }
            flushLoose(loose, result)
            val children = setting.children.filter { it.isVisible }
            if (children.isNotEmpty()) result += setting.name.uppercase() to children
        }
        flushLoose(loose, result)
        return result
    }

    private fun flushLoose(loose: MutableList<Setting<*>>, into: MutableList<Pair<String, List<Setting<*>>>>) {
        if (loose.isEmpty()) return
        into += GENERAL_SECTION to loose.toList()
        loose.clear()
    }

    private fun topLevel(mod: Module): List<Setting<*>> =
        mod.allSettings().filter { it.isVisible && it.parentDropdown == null }
}
