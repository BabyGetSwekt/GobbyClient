package gobby.gui

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
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
private const val TITLE_BAR_HEIGHT = 22f
private const val BOTTOM_BAR_HEIGHT = 28f
private const val SIDE_PAD = 8f
private const val CLOSE_BUTTON_SIZE = 14f
private const val TITLE_X = 26f
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
private const val SCROLL_ACCELERATION = 1.8f
private const val SCROLL_INNER_PADDING = 2f
private const val PANEL_CORNER = 5f
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
    private var dirty = false
    private var forceClose = false

    private val overlay by UIBlock(ComponentTheme.OVERLAY).constrain {
        width = 100.percent
        height = 100.percent
    } childOf window

    private val panel by UIRoundedRectangle(PANEL_CORNER).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        width = PANEL_WIDTH_PERCENT.percent
        height = PANEL_HEIGHT_PERCENT.percent
        color = ComponentTheme.PANEL_BG.toConstraint()
    } childOf window

    private val titleBar by UIBlock(ComponentTheme.TITLE_BAR_BG).constrain {
        width = 100.percent
        height = TITLE_BAR_HEIGHT.pixels
    } childOf panel

    private val titleText by UIText("Mob ESP", shadow = true).constrain {
        x = TITLE_X.pixels
        y = CenterConstraint()
        color = ComponentTheme.TEXT.toConstraint()
        fontProvider = StyledFontProvider
    } childOf titleBar

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

    private val headerOn by UIText("ON", shadow = false).constrain {
        x = MobEspColumns.SIDE_PAD.pixels
        y = CenterConstraint()
        color = ComponentTheme.TEXT_MUTED.toConstraint()
        textScale = HEADER_SCALE.pixels
        fontProvider = StyledFontProvider
    } childOf headerBar

    private val headerName by UIText("MOB NAME", shadow = false).constrain {
        x = MobEspColumns.NAME_X.pixels
        y = CenterConstraint()
        color = ComponentTheme.TEXT_MUTED.toConstraint()
        textScale = HEADER_SCALE.pixels
        fontProvider = StyledFontProvider
    } childOf headerBar

    private val headerMatch by UIText("MATCH", shadow = false).constrain {
        x = MobEspColumns.DROPDOWN_LEFT_FROM_RIGHT.pixels(alignOpposite = true)
        y = CenterConstraint()
        color = ComponentTheme.TEXT_MUTED.toConstraint()
        textScale = HEADER_SCALE.pixels
        fontProvider = StyledFontProvider
    } childOf headerBar

    private val scrollPanel by GobbyScrollPanel(
        emptyString = "No mobs configured - click + Add",
        innerPadding = SCROLL_INNER_PADDING,
        pixelsPerScroll = SCROLL_PIXELS_PER,
        scrollAcceleration = SCROLL_ACCELERATION
    ).constrain {
        x = SIDE_PAD.pixels
        y = LIST_TOP.pixels
        width = 100.percent - (SIDE_PAD * 2).pixels
        height = 100.percent - LIST_BOTTOM_RESERVED.pixels
    } childOf panel

    private val bottomBar by UIBlock(ComponentTheme.TITLE_BAR_BG).constrain {
        y = 0.pixels(alignOpposite = true)
        width = 100.percent
        height = BOTTOM_BAR_HEIGHT.pixels
    } childOf panel

    private val confirmModal = ConfirmModal(
        window,
        message = "Return without saving?",
        confirmText = "Discard",
        cancelText = "Cancel",
        font = StyledFontProvider,
        onConfirm = {
            forceClose = true
            displayScreen(null)
        },
        onCancel = { dismissConfirm() }
    )

    init {
        GobbyCloseButton { tryReturn() }.constrain {
            x = SIDE_PAD.pixels
            y = CenterConstraint()
            width = CLOSE_BUTTON_SIZE.pixels
            height = CLOSE_BUTTON_SIZE.pixels
        } childOf titleBar

        GobbyButton("+ Add", ComponentTheme.ACCENT, ComponentTheme.ACCENT_HOVER, font = StyledFontProvider) { addNewRow() }.constrain {
            x = SIDE_PAD.pixels(alignOpposite = true)
            y = CenterConstraint()
            width = ADD_BUTTON_WIDTH.pixels
            height = ADD_BUTTON_HEIGHT.pixels
        } childOf titleBar

        GobbyButton("Return", ComponentTheme.ACCENT_OFF, ComponentTheme.ROW_BG_HOVER, font = StyledFontProvider) { tryReturn() }.constrain {
            x = SIDE_PAD.pixels
            y = CenterConstraint()
            width = RETURN_BUTTON_WIDTH.pixels
            height = ACTION_BUTTON_HEIGHT.pixels
        } childOf bottomBar

        GobbyButton("Save & Exit", ComponentTheme.ACCENT, ComponentTheme.ACCENT_HOVER, font = StyledFontProvider) { saveAndExit() }.constrain {
            x = SIDE_PAD.pixels(alignOpposite = true)
            y = CenterConstraint()
            width = SAVE_BUTTON_WIDTH.pixels
            height = ACTION_BUTTON_HEIGHT.pixels
        } childOf bottomBar

        overlay.onMouseClick { tryReturn() }

        MobHighlighterConfig.getEntries().forEach { addRow(it) }
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
            onChange = { dirty = true },
            onRemove = { removeRow(it) }
        ).constrain {
            x = 0.pixels
            y = SiblingConstraint(ROW_GAP)
        }
        rows.add(row)
        row childOf scrollPanel.scrollArea
        if (entry.name.isEmpty()) dirty = true
    }

    private fun removeRow(row: MobEspEntryComponent) {
        rows.remove(row)
        scrollPanel.scrollArea.removeChild(row)
        dirty = true
    }

    private fun tryReturn() {
        if (dirty) confirmModal.show() else displayScreen(null)
    }

    private fun dismissConfirm() {
        confirmModal.dismiss()
    }

    private fun saveAndExit() {
        MobHighlighterConfig.replaceAll(rows.map { it.toEntry() })
        MobHighlighterConfig.save()
        forceClose = true
        displayScreen(null)
    }

    override fun onClose() {
        if (dirty && !forceClose) {
            confirmModal.show()
            return
        }
        super.onClose()
    }

    companion object {
        fun open() {
            displayScreen(MobEspScreen())
        }
    }
}
