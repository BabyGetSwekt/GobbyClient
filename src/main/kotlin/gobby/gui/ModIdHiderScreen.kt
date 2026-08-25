package gobby.gui

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.*
import gobby.features.skyblock.ModIdHider
import gobby.gui.components.*
import gobby.gui.font.StyledFontProvider
import java.awt.Color

private const val PANEL_WIDTH = 300f
private const val PANEL_HEIGHT = 260f
private const val BOTTOM_BAR_HEIGHT = 22f
private const val INPUT_ROW_Y = 28f
private const val INPUT_ROW_HEIGHT = 16f
private const val ADD_BUTTON_WIDTH = 34f
private const val LIST_TOP = 50f
private const val LIST_BOTTOM_RESERVED = 78f
private const val SAVE_BUTTON_WIDTH = 80f
private const val SAVE_BUTTON_HEIGHT = 16f
private const val HINT_RIGHT_OFFSET = 88f
private const val TOOLTIP_BOTTOM_OFFSET = 2f
private const val ROW_GAP = 2f
private const val COMPACT_SCROLL_STEP = 30f
private const val PROTECTED_MOD_ID = "gobbyclient"

class ModIdHiderScreen private constructor() : WindowScreen(
    version = ElementaVersion.V6,
    drawDefaultBackground = false
) {

    private val entries = mutableListOf<ModIdEntryComponent>()

    private val panel = GobbyPanel(
        window,
        title = "Mod ID Hider",
        font = StyledFontProvider,
        closeButton = false,
        bottomBarHeight = BOTTOM_BAR_HEIGHT,
        onDismiss = { tryClose() }
    ).constrain {
        width = PANEL_WIDTH.pixels
        height = PANEL_HEIGHT.pixels
    }

    private val textField = GobbyTextField(
        placeholder = "Enter mod ID...",
        font = StyledFontProvider,
        onSubmit = { addCurrentInput() }
    ).constrain {
        x = ComponentTheme.SIDE_PAD.pixels
        y = INPUT_ROW_Y.pixels
        width = 100.percent - (ComponentTheme.SIDE_PAD * 2 + ADD_BUTTON_WIDTH + ComponentTheme.TITLE_GAP).pixels
        height = INPUT_ROW_HEIGHT.pixels
    } childOf panel

    private val scrollPanel = panel.contentArea(
        GobbyScrollPanel(emptyString = "No hidden mods", innerPadding = ROW_GAP, pixelsPerScroll = COMPACT_SCROLL_STEP),
        LIST_TOP, LIST_BOTTOM_RESERVED
    )

    private val tooltip = GobbyTooltip(panel, "Requires a game restart to apply!", StyledFontProvider).constrain {
        x = HINT_RIGHT_OFFSET.pixels(alignOpposite = true)
        y = TOOLTIP_BOTTOM_OFFSET.pixels(alignOpposite = true)
    }

    private val snitchToast = GobbyToast(window, "Why would u snitch on yourself?", StyledFontProvider)

    private val guard = UnsavedChangesGuard(
        window,
        message = "You have unsaved changes!",
        discardText = "Leave",
        keepText = "Save",
        font = StyledFontProvider,
        onDiscard = { closeWithoutSaving() },
        onKeep = { saveAndClose() }
    )

    init {
        GobbyButton("Add", ComponentTheme.ACCENT, ComponentTheme.ACCENT_HOVER, font = StyledFontProvider) { addCurrentInput() }.constrain {
            x = ComponentTheme.SIDE_PAD.pixels(alignOpposite = true)
            y = INPUT_ROW_Y.pixels
            width = ADD_BUTTON_WIDTH.pixels
            height = INPUT_ROW_HEIGHT.pixels
        } childOf panel

        panel.bottomBar?.let { bar ->
            GobbyHintIcon(tooltip, StyledFontProvider).constrain {
                x = HINT_RIGHT_OFFSET.pixels(alignOpposite = true)
                y = CenterConstraint()
                width = ComponentTheme.ICON_SIZE.pixels
                height = ComponentTheme.ICON_SIZE.pixels
            } childOf bar

            GobbyButton("Save & Close", ComponentTheme.ACCENT, ComponentTheme.ACCENT_HOVER, font = StyledFontProvider) { saveAndClose() }.constrain {
                x = ComponentTheme.TITLE_GAP.pixels(alignOpposite = true)
                y = CenterConstraint()
                width = SAVE_BUTTON_WIDTH.pixels
                height = SAVE_BUTTON_HEIGHT.pixels
            } childOf bar
        }

        ModIdHider.getHiddenMods().forEach(::addEntry)
    }

    override fun onClose() {
        if (guard.shouldBlockClose()) return
        super.onClose()
    }

    private fun tryClose() = guard.requestClose { displayScreen(null) }

    private fun addCurrentInput() {
        val modId = textField.getText().trim().lowercase()
        if (modId.isEmpty() || entries.any { it.modId == modId }) return
        addEntry(modId)
        textField.clear()
        guard.dirty = true
    }

    private fun addEntry(modId: String) {
        val entry = ModIdEntryComponent(modId, ::removeEntry).constrain {
            x = 0.pixels
            y = SiblingConstraint(ROW_GAP)
        }
        entries.add(entry)
        entry childOf scrollPanel.scrollArea
    }

    private fun removeEntry(entry: ModIdEntryComponent) {
        if (entry.modId == PROTECTED_MOD_ID) {
            snitchToast.show()
            return
        }
        entries.remove(entry)
        scrollPanel.scrollArea.removeChild(entry)
        guard.dirty = true
    }

    private fun closeWithoutSaving() {
        guard.allowClose()
        displayScreen(null)
    }

    private fun saveAndClose() {
        ModIdHider.replaceAll(entries.map { it.modId })
        ModIdHider.save()
        closeWithoutSaving()
    }

    companion object {
        fun open() = displayScreen(ModIdHiderScreen())
    }
}
