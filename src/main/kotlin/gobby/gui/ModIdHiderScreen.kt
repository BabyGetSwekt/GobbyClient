package gobby.gui

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.components.input.UITextInput
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gobby.Gobbyclient.Companion.mc
import gobby.features.skyblock.ModIdHider
import gobby.gui.components.GobbyScrollPanel
import gobby.gui.components.ModIdEntryComponent
import java.awt.Color

class ModIdHiderScreen private constructor() : WindowScreen(
    version = ElementaVersion.V6,
    drawDefaultBackground = false
) {

    private val entries = mutableListOf<ModIdEntryComponent>()
    private var dirty = false
    private var forceClose = false

    private val overlay by UIBlock(Color(0, 0, 0, 100)).constrain {
        width = 100.percent
        height = 100.percent
    } childOf window

    private val panel by UIRoundedRectangle(5f).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        width = 300.pixels
        height = 260.pixels
        color = Color(18, 18, 22, 240).toConstraint()
    } childOf window

    private val titleBar by UIBlock(Color(26, 26, 32, 255)).constrain {
        width = 100.percent
        height = 22.pixels
    } childOf panel

    private val titleText by UIText("Mod ID Hider", shadow = true).constrain {
        x = 8.pixels
        y = CenterConstraint()
        color = Color(210, 210, 215).toConstraint()
    } childOf titleBar

    private val inputRow by UIBlock(Color(0, 0, 0, 0)).constrain {
        x = 8.pixels
        y = 28.pixels
        width = 100.percent - 16.pixels
        height = 16.pixels
    } childOf panel

    private val inputBg by UIRoundedRectangle(3f).constrain {
        width = 100.percent - 40.pixels
        height = 100.percent
        color = Color(12, 12, 16, 220).toConstraint()
    } childOf inputRow

    private val textInput by UITextInput(placeholder = "Enter mod ID...").constrain {
        x = 4.pixels
        y = CenterConstraint()
        width = 100.percent - 8.pixels
        height = 9.pixels
        color = Color(200, 200, 200).toConstraint()
    } childOf inputBg

    private val addButton by UIRoundedRectangle(3f).constrain {
        x = 0.pixels(alignOpposite = true)
        y = CenterConstraint()
        width = 34.pixels
        height = 100.percent
        color = Color(30, 90, 50, 220).toConstraint()
    } childOf inputRow

    private val addLabel by UIText("Add", shadow = true).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color(210, 210, 215).toConstraint()
    } childOf addButton

    private val scrollPanel by GobbyScrollPanel(
        emptyString = "No hidden mods",
        innerPadding = 2f,
        pixelsPerScroll = 30f,
        scrollAcceleration = 1.8f
    ).constrain {
        x = 8.pixels
        y = 50.pixels
        width = 100.percent - 16.pixels
        height = 100.percent - 78.pixels
    } childOf panel

    private val bottomBar by UIBlock(Color(26, 26, 32, 255)).constrain {
        y = 0.pixels(alignOpposite = true)
        width = 100.percent
        height = 22.pixels
    } childOf panel

    private val infoIcon by UIRoundedRectangle(7f).constrain {
        x = 0.pixels(alignOpposite = true) - 88.pixels
        y = CenterConstraint()
        width = 14.pixels
        height = 14.pixels
        color = Color(140, 30, 30, 220).toConstraint()
    } childOf bottomBar

    private val infoQuestion by UIText("?", shadow = false).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color(255, 255, 255).toConstraint()
    } childOf infoIcon

    private val tooltip by UIRoundedRectangle(3f).constrain {
        x = 0.pixels(alignOpposite = true) - 88.pixels
        y = 0.pixels(alignOpposite = true) - 2.pixels
        width = 175.pixels
        height = 16.pixels
        color = Color(10, 10, 14, 240).toConstraint()
    } childOf panel

    private val tooltipText by UIText("Requires a game restart to apply!", shadow = true).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color(200, 60, 60).toConstraint()
        textScale = 0.75.pixels
    } childOf tooltip

    private val saveButton by UIRoundedRectangle(3f).constrain {
        x = 0.pixels(alignOpposite = true) - 4.pixels
        y = CenterConstraint()
        width = 80.pixels
        height = 16.pixels
        color = Color(30, 90, 50, 220).toConstraint()
    } childOf bottomBar

    private val saveLabel by UIText("Save & Close", shadow = true).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color(210, 210, 215).toConstraint()
        textScale = 0.85.pixels
    } childOf saveButton

    // Easter egg I guess
    private val snitchPopup by UIRoundedRectangle(5f).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        width = 200.pixels
        height = 40.pixels
        color = Color(30, 10, 10, 245).toConstraint()
    } childOf window

    private val snitchText by UIText("Why would u snitch on yourself?", shadow = true).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color(255, 80, 80).toConstraint()
        textScale = 0.85.pixels
    } childOf snitchPopup

    private val dialogOverlay by UIBlock(Color(0, 0, 0, 150)).constrain {
        width = 100.percent
        height = 100.percent
    } childOf window

    private val dialogBox by UIRoundedRectangle(5f).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        width = 220.pixels
        height = 70.pixels
        color = Color(22, 22, 28, 245).toConstraint()
    } childOf dialogOverlay

    private val dialogText by UIText("You have unsaved changes!", shadow = true).constrain {
        x = CenterConstraint()
        y = 10.pixels
        color = Color(210, 210, 215).toConstraint()
        textScale = 0.85.pixels
    } childOf dialogBox

    private val dialogSaveBtn by UIRoundedRectangle(3f).constrain {
        x = CenterConstraint() - 50.pixels
        y = 0.pixels(alignOpposite = true) - 10.pixels
        width = 70.pixels
        height = 18.pixels
        color = Color(30, 90, 50, 220).toConstraint()
    } childOf dialogBox

    private val dialogSaveLabel by UIText("Save", shadow = true).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color(210, 210, 215).toConstraint()
        textScale = 0.85.pixels
    } childOf dialogSaveBtn

    private val dialogLeaveBtn by UIRoundedRectangle(3f).constrain {
        x = CenterConstraint() + 50.pixels
        y = 0.pixels(alignOpposite = true) - 10.pixels
        width = 70.pixels
        height = 18.pixels
        color = Color(120, 30, 30, 220).toConstraint()
    } childOf dialogBox

    private val dialogLeaveLabel by UIText("Leave", shadow = true).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color(210, 210, 215).toConstraint()
        textScale = 0.85.pixels
    } childOf dialogLeaveBtn

    init {
        tooltip.hide(instantly = true)
        snitchPopup.hide(instantly = true)
        dialogOverlay.hide(instantly = true)

        infoIcon.onMouseEnter { tooltip.unhide() }
        infoIcon.onMouseLeave { tooltip.hide() }

        inputBg.enableEffect(OutlineEffect(Color(40, 40, 50), 1f))
        inputBg.onMouseClick { textInput.grabWindowFocus() }

        addButton.applyHoverAnimation(Color(30, 90, 50, 220), Color(40, 130, 70, 240))
        addButton.onMouseClick { addCurrentInput() }

        textInput.onKeyType { typedChar, _ ->
            if (typedChar == '\r') addCurrentInput()
        }

        saveButton.applyHoverAnimation(Color(30, 90, 50, 220), Color(40, 130, 70, 240))
        saveButton.onMouseClick { saveAndClose() }

        overlay.onMouseClick { tryClose() }

        dialogSaveBtn.applyHoverAnimation(Color(30, 90, 50, 220), Color(40, 130, 70, 240))
        dialogSaveBtn.onMouseClick { saveAndClose() }

        dialogLeaveBtn.applyHoverAnimation(Color(120, 30, 30, 220), Color(180, 40, 40, 240))
        dialogLeaveBtn.onMouseClick {
            forceClose = true
            displayScreen(null)
        }

        dialogOverlay.onMouseClick { it.stopPropagation() }

        populateEntries()
    }

    override fun close() {
        if (dirty && !forceClose) {
            dialogOverlay.unhide()
            return
        }
        super.close()
    }

    private fun tryClose() {
        if (dirty) {
            dialogOverlay.unhide()
        } else {
            displayScreen(null)
        }
    }

    private fun addCurrentInput() {
        val id = textInput.getText().trim().lowercase()
        if (id.isEmpty()) return
        if (entries.any { it.modId == id }) return

        addEntry(id)
        textInput.setText("")
        dirty = true
    }

    private fun addEntry(modId: String) {
        val entry = ModIdEntryComponent(modId) { comp ->
            if (comp.modId == "gobbyclient") {
                showSnitchPopup()
                return@ModIdEntryComponent
            }
            entries.remove(comp)
            scrollPanel.scrollArea.removeChild(comp)
            dirty = true
        }.constrain {
            x = 0.pixels
            y = SiblingConstraint(2f)
        }

        entries.add(entry)
        entry childOf scrollPanel.scrollArea
    }

    private fun showSnitchPopup() {
        snitchPopup.unhide()
        Thread {
            Thread.sleep(2000)
            mc.send { snitchPopup.hide() }
        }.apply { isDaemon = true }.start()
    }

    private fun populateEntries() {
        for (modId in ModIdHider.getHiddenMods()) {
            addEntry(modId)
        }
    }

    private fun saveAndClose() {
        ModIdHider.replaceAll(entries.map { it.modId })
        ModIdHider.save()
        forceClose = true
        displayScreen(null)
    }

    companion object {
        fun open() {
            displayScreen(ModIdHiderScreen())
        }

        private fun UIRoundedRectangle.applyHoverAnimation(baseColor: Color, hoverColor: Color) {
            onMouseEnter {
                animate { setColorAnimation(Animations.OUT_EXP, 0.15f, hoverColor.toConstraint()) }
            }
            onMouseLeave {
                animate { setColorAnimation(Animations.OUT_EXP, 0.2f, baseColor.toConstraint()) }
            }
        }
    }
}
