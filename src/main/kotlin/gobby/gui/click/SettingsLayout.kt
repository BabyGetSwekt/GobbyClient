package gobby.gui.click

private const val STRING_EXTRA_H = 16
private const val COLUMN_COUNT = 2

internal data class PanelFrame(val x: Int, val y: Int)

internal data class PlacedRow(val setting: Setting<*>, val x: Int, val y: Int, val w: Int, val h: Int) {
    val rect: Rect get() = Rect(x, y, w, h)
}

internal data class SettingGroup(val title: String, val align: SettingAlign, val settings: List<Setting<*>>)

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

    fun rowHeight(setting: Setting<*>): Int = when (setting) {
        is StringSetting -> SETTINGS_ROW_H + STRING_EXTRA_H
        is ModelPreviewSetting -> SettingsPreview.cardHeight()
        else -> SETTINGS_ROW_H
    }

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
        return groups(mod).map { group ->
            val target = columnFor(group.align, columnY)
            place(group, columnX[target], columnY[target], columnW).also { columnY[target] = it.bottom() }
        }
    }

    private fun columnFor(align: SettingAlign, columnY: IntArray): Int = when (align) {
        SettingAlign.LEFT -> 0
        SettingAlign.RIGHT -> COLUMN_COUNT - 1
        SettingAlign.AUTO -> columnY.indices.minBy { columnY[it] }
    }

    fun contentHeight(frame: PanelFrame, blocks: List<PlacedBlock>): Int =
        (blocks.maxOfOrNull { it.bottom() } ?: contentTop(frame)) - contentTop(frame)

    private fun PlacedBlock.bottom(): Int = cardY + cardH + SETTINGS_SECTION_GAP

    private fun place(group: SettingGroup, x: Int, y: Int, w: Int): PlacedBlock {
        val cardY = y + SETTINGS_SECTION_H
        var rowY = cardY + SETTINGS_CARD_PAD
        val rows = group.settings.map { setting ->
            val h = rowHeight(setting)
            PlacedRow(setting, x + SETTINGS_CARD_PAD, rowY, w - SETTINGS_CARD_PAD * 2, h).also { rowY += h }
        }
        return PlacedBlock(group.title, x, y, w, cardY, rowY - cardY + SETTINGS_CARD_PAD, rows)
    }

    internal fun groups(mod: Module): List<SettingGroup> {
        val bySection = LinkedHashMap<SettingSection, MutableList<Setting<*>>>()
        topLevel(mod).forEach { setting ->
            val members = if (setting is DropDownSetting) setting.children.filter { it.isVisible } else listOf(setting)
            if (members.isNotEmpty()) bySection.getOrPut(sectionOf(setting)) { mutableListOf() } += members
        }
        return bySection.map { (section, settings) ->
            SettingGroup(section.title.uppercase(), section.align, settings)
        }
    }

    private fun sectionOf(setting: Setting<*>): SettingSection =
        setting.section ?: if (setting is DropDownSetting) setting.ownSection else MAIN_SECTION

    private fun topLevel(mod: Module): List<Setting<*>> =
        mod.allSettings().filter { it.isVisible && it.parentDropdown == null }
}
