package gobby.gui

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.XConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gobby.Gobbyclient.Companion.mc
import gobby.features.render.DEFAULT_MOB_COLOR
import gobby.features.render.MobEntry
import gobby.features.render.MobHighlighterConfig
import gobby.gui.components.*
import gobby.gui.font.StyledFontProvider
import kotlin.math.roundToInt

private const val PANEL_WIDTH_PERCENT = 92f
private const val PANEL_HEIGHT_PERCENT = 90f
private const val BOTTOM_BAR_HEIGHT = 28f
private const val SIDE_PAD = 8f
private const val ADD_BUTTON_WIDTH = 56f
private const val ADD_BUTTON_HEIGHT = 15f
private const val EXPLANATION_LINE1_Y = 27f
private const val EXPLANATION_LINE2_Y = 37f
private const val HEADER_Y = 52f
private const val LIST_TOP = 66f
private const val LIST_BOTTOM_RESERVED = LIST_TOP + BOTTOM_BAR_HEIGHT + 6f
private const val ACTION_BUTTON_HEIGHT = 18f
private const val RETURN_BUTTON_WIDTH = 84f
private const val SAVE_BUTTON_WIDTH = 104f
private const val SMALL_SCALE = 0.72f
private const val HEADER_SCALE = 0.68f
private const val ROW_GAP = 2f
private const val HEADER_BAR_HEIGHT = 12f
private const val SCROLL_PIXELS_PER = 30f
private const val SCROLL_INNER_PADDING = 2f
private const val EXPLANATION_LINE1 = "Toggle a mob on, name it, choose match mode, and pick its color."
private const val EXPLANATION_LINE2 = "equals = exact name   contains = partial   (case-insensitive)"
private const val TARGET_GUI_SCALED_HEIGHT = 360f
private const val MIN_GUI_SCALE = 2
private const val MAX_GUI_SCALE = 6

private fun mobEspGuiScale(): Int =
    (mc.window.height / TARGET_GUI_SCALED_HEIGHT).roundToInt().coerceIn(MIN_GUI_SCALE, MAX_GUI_SCALE)

class MobEspScreen private constructor() : WindowScreen(
    version = ElementaVersion.V10,
    drawDefaultBackground = true,
    newGuiScale = mobEspGuiScale()
) {

    private val rows = mutableListOf<MobEspEntryComponent>()
    private var headersShown = true

    private val panel = GobbyPanel(
        window,
        title = "Mob ESP",
        font = StyledFontProvider,
        bottomBarHeight = BOTTOM_BAR_HEIGHT,
        onDismiss = { tryReturn() }
    ).constrain {
        width = PANEL_WIDTH_PERCENT.percent
        height = PANEL_HEIGHT_PERCENT.percent
    }

    private val explanationLine1 by UIText(EXPLANATION_LINE1, shadow = true).constrain {
        x = SIDE_PAD.pixels
        y = EXPLANATION_LINE1_Y.pixels
        color = ComponentTheme.TEXT_DIM.toConstraint()
        textScale = SMALL_SCALE.pixels
        fontProvider = StyledFontProvider
    } childOf panel

    private val explanationLine2 by UIText(EXPLANATION_LINE2, shadow = true).constrain {
        x = SIDE_PAD.pixels
        y = EXPLANATION_LINE2_Y.pixels
        color = ComponentTheme.TEXT_MUTED.toConstraint()
        textScale = SMALL_SCALE.pixels
        fontProvider = StyledFontProvider
    } childOf panel

    private val headerBar by UIContainer().constrain {
        x = SIDE_PAD.pixels
        y = HEADER_Y.pixels
        width = 100.percent - (SIDE_PAD * 2 + SCROLL_BAR_RESERVE).pixels
        height = HEADER_BAR_HEIGHT.pixels
    } childOf panel

    private val scrollPanel by GobbyScrollPanel(
        emptyString = "Click \"+ Add\" to add mobs",
        innerPadding = SCROLL_INNER_PADDING,
        pixelsPerScroll = SCROLL_PIXELS_PER,
    ).constrain {
        x = SIDE_PAD.pixels
        y = LIST_TOP.pixels
        width = 100.percent - (SIDE_PAD * 2).pixels
        height = 100.percent - LIST_BOTTOM_RESERVED.pixels
    } childOf panel

    private val guard = UnsavedChangesGuard(
        window,
        message = "Return without saving?",
        discardText = "Discard",
        keepText = "Cancel",
        font = StyledFontProvider,
        onDiscard = { closeWithoutSaving() }
    )

    init {
        columnHeader("ON", MobEspColumns.SIDE_PAD.pixels)
        columnHeader("MOB NAME", MobEspColumns.NAME_X.pixels)
        columnHeader("MATCH", MobEspColumns.DROPDOWN_LEFT_FROM_RIGHT.pixels(alignOpposite = true))

        GobbyButton("+ Add", ComponentTheme.ACCENT, ComponentTheme.ACCENT_HOVER, font = StyledFontProvider) { addNewRow() }.constrain {
            x = SIDE_PAD.pixels(alignOpposite = true)
            y = CenterConstraint()
            width = ADD_BUTTON_WIDTH.pixels
            height = ADD_BUTTON_HEIGHT.pixels
        } childOf panel.titleBar

        panel.bottomBar?.let { bar ->
            GobbyButton("Return", ComponentTheme.ACCENT_OFF, ComponentTheme.ROW_BG_HOVER, font = StyledFontProvider) { tryReturn() }.constrain {
                x = SIDE_PAD.pixels
                y = CenterConstraint()
                width = RETURN_BUTTON_WIDTH.pixels
                height = ACTION_BUTTON_HEIGHT.pixels
            } childOf bar

            GobbyButton("Save & Exit", ComponentTheme.ACCENT, ComponentTheme.ACCENT_HOVER, font = StyledFontProvider) { saveAndExit() }.constrain {
                x = SIDE_PAD.pixels(alignOpposite = true)
                y = CenterConstraint()
                width = SAVE_BUTTON_WIDTH.pixels
                height = ACTION_BUTTON_HEIGHT.pixels
            } childOf bar
        }

        MobHighlighterConfig.getEntries().forEach { addRow(it) }
        updateHeaderVisibility()
    }

    private fun columnHeader(text: String, xPosition: XConstraint): UIText =
        UIText(text, shadow = false).constrain {
            x = xPosition
            y = CenterConstraint()
            color = ComponentTheme.TEXT_MUTED.toConstraint()
            textScale = HEADER_SCALE.pixels
            fontProvider = StyledFontProvider
        } childOf headerBar

    private fun updateHeaderVisibility() {
        val shouldShow = rows.isNotEmpty()
        if (shouldShow == headersShown) return
        headersShown = shouldShow
        if (shouldShow) headerBar.unhide() else headerBar.hide(instantly = true)
    }

    private fun addNewRow() {
        val previousColor = rows.lastOrNull()?.toEntry()?.color ?: DEFAULT_MOB_COLOR.rgb
        addRow(MobEntry(color = previousColor))
    }

    private fun addRow(entry: MobEntry) {
        val row = MobEspEntryComponent(
            window = window,
            entry = entry,
            font = StyledFontProvider,
            onChange = { guard.dirty = true },
            onRemove = { removeRow(it) }
        ).constrain {
            x = 0.pixels
            y = SiblingConstraint(ROW_GAP)
        }
        rows.add(row)
        row childOf scrollPanel.scrollArea
        if (entry.name.isEmpty()) guard.dirty = true
        updateHeaderVisibility()
    }

    private fun removeRow(row: MobEspEntryComponent) {
        rows.remove(row)
        scrollPanel.scrollArea.removeChild(row)
        guard.dirty = true
        updateHeaderVisibility()
    }

    private fun tryReturn() = guard.requestClose { displayScreen(null) }

    private fun closeWithoutSaving() {
        guard.allowClose()
        displayScreen(null)
    }

    private fun saveAndExit() {
        MobHighlighterConfig.replaceAll(rows.map { it.toEntry() })
        MobHighlighterConfig.save()
        closeWithoutSaving()
    }

    override fun onClose() {
        if (guard.shouldBlockClose()) return
        super.onClose()
    }

    companion object {
        fun open() {
            displayScreen(MobEspScreen())
        }
    }
}
