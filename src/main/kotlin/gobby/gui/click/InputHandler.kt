package gobby.gui.click

import org.lwjgl.glfw.GLFW
import kotlin.math.abs

object InputHandler {

    fun handleMouseClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean {
        gui.listeningKeybind?.let { kb ->
            if (button in GLFW.GLFW_MOUSE_BUTTON_LEFT..GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                kb.value = KeybindSetting.MOUSE_OFFSET + button
                gui.listeningKeybind = null
                ConfigManager.save()
                return true
            }
        }
        gui.hexEditSetting = null
        gui.numberEditSetting = null
        gui.stringEditSetting?.let { StringInput.commit(gui, it) }

        gui.view?.let { return it.handleClick(gui, mx, my, button) }

        if (mx <= gui.panelX + SIDEBAR_W_SETTINGS) return SettingsSidebar.handleClick(gui, mx, my)

        if (gui.settingsModule == null && SearchBar.handleClick(gui, mx, my)) {
            gui.draggingSearch = true
            return true
        }

        val mod = gui.settingsModule
        return if (mod != null) {
            ModuleSettingsComponent.handleClick(gui, mod, mx, my, button)
        } else {
            ModuleGridComponent.handleClick(gui, mx, my, button)
        }
    }

    internal fun dispatchSettingClick(gui: ClickGUI, row: PlacedRow, mx: Int, my: Int, button: Int): Boolean {
        gui.listeningKeybind = null
        if (row.setting.recordsUndo()) SettingHistory.record(row.setting)
        when (val setting = row.setting) {
            is KeybindSetting -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    setting.value = 0
                    ConfigManager.save()
                } else {
                    gui.listeningKeybind = setting
                }
            }
            is BooleanSetting -> {
                setting.value = !setting.value
                ConfigManager.save()
            }
            is NumberSetting -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    NumberInput.begin(gui, setting)
                    placeCaret(gui.numberField, SettingsControls.numberTextOrigin(row, gui.numberField.text), mx)
                } else {
                    val track = SettingsControls.trackRect(row)
                    gui.draggingSlider = setting
                    gui.sliderBaseX = track.x
                    gui.sliderBaseW = track.w
                    updateSlider(setting, mx, track.x, track.w)
                }
            }
            is RangeSetting -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    val track = SettingsControls.trackRect(row)
                    gui.draggingRange = setting
                    gui.sliderBaseX = track.x
                    gui.sliderBaseW = track.w
                    val lowX = track.x + (track.w * setting.progress(setting.value.start)).toInt()
                    val highX = track.x + (track.w * setting.progress(setting.value.endInclusive)).toInt()
                    gui.draggingRangeHigh = abs(mx - highX) <= abs(mx - lowX)
                    updateRange(setting, mx, track.x, track.w, gui.draggingRangeHigh)
                }
            }
            is StringSetting -> {
                StringInput.begin(gui, setting)
                placeCaret(gui.stringField, SettingsControls.stringTextOrigin(row), mx)
            }
            is SelectorSetting -> gui.openSelector = if (gui.openSelector === setting) null else setting
            is ColorSetting -> setting.expanded = !setting.expanded
            is ActionSetting -> setting.action()
            is HudButton -> setting.onClick()
            is DropDownSetting -> setting.expanded = !setting.expanded
            is ModelPreviewSetting -> {}
        }
        return true
    }

    private fun placeCaret(field: TextField, originX: Int, mx: Int) =
        field.placeCaret(TextFieldView.caretIndexAt(field.text, originX, mx, SETTINGS_VALUE_SCALE), extend = false)

    private fun updateSlider(setting: NumberSetting, mx: Int, baseX: Int, baseW: Int) {
        setting.setFromProgress(((mx - baseX).toFloat() / baseW).coerceIn(0f, 1f))
        ConfigManager.save()
    }

    private fun updateRange(setting: RangeSetting, mx: Int, baseX: Int, baseW: Int, high: Boolean) {
        val progress = ((mx - baseX).toFloat() / baseW).coerceIn(0f, 1f)
        val raw = setting.min + (setting.max - setting.min) * progress
        if (high) setting.high = raw else setting.low = raw
        ConfigManager.save()
    }

    fun handleMouseDrag(gui: ClickGUI, currentX: Double, currentY: Double): Boolean {
        gui.draggingSlider?.let {
            updateSlider(it, currentX.toInt(), gui.sliderBaseX, gui.sliderBaseW)
            return true
        }
        gui.draggingRange?.let {
            updateRange(it, currentX.toInt(), gui.sliderBaseX, gui.sliderBaseW, gui.draggingRangeHigh)
            return true
        }
        return ColorPickerInput.handleDrag(gui, currentX, currentY)
    }

    fun handleMouseRelease(gui: ClickGUI) {
        gui.draggingSlider = null
        gui.draggingRange = null
        ColorPickerInput.clearDragging(gui)
    }

    fun handleScroll(gui: ClickGUI, mouseX: Int, mouseY: Int, verticalAmount: Double): Boolean {
        gui.view?.let { return it.handleScroll(gui, mouseX, mouseY, verticalAmount) }
        val mod = gui.settingsModule
        return if (mod != null) {
            ModuleSettingsComponent.handleScroll(gui, mod, mouseX, mouseY, verticalAmount)
        } else {
            ModuleGridComponent.handleScroll(gui, mouseX, mouseY, verticalAmount)
        }
    }

    fun handleKeyPress(gui: ClickGUI, key: Int): Boolean {
        gui.view?.let { if (it.handleKey(gui, key)) return true }
        gui.listeningKeybind?.let { kb ->
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                kb.value = 0
                gui.listeningKeybind = null
                ConfigManager.save()
                return true
            }
            kb.value = key
            gui.listeningKeybind = null
            gui.suppressNextChar = true
            ConfigManager.save()
            return true
        }

        gui.hexEditSetting?.let { return HexInput.handleKey(gui, it, key) }

        if (key == GLFW.GLFW_KEY_Z && Modifiers.ctrl() && SettingHistory.undo()) return true

        if (Modifiers.ctrl()) expandedColorSetting(gui)?.let { setting ->
            when (key) {
                GLFW.GLFW_KEY_C -> HexInput.copy(gui, setting)
                GLFW.GLFW_KEY_V -> HexInput.paste(gui, setting)
                else -> return@let
            }
            return true
        }

        gui.numberEditSetting?.let { return NumberInput.handleKey(gui, it, key) }

        gui.stringEditSetting?.let { return StringInput.handleKey(gui, it, key) }

        if (gui.settingsModule == null && SearchBar.handleKey(key)) return true

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (gui.settingsModule != null) gui.closeSettings() else gui.onClose()
            return true
        }
        return false
    }

    fun handleCharTyped(gui: ClickGUI, chr: Char): Boolean {
        if (gui.suppressNextChar) {
            gui.suppressNextChar = false
            return true
        }
        gui.view?.let { return it.handleChar(chr) }
        if (gui.listeningKeybind != null) return true

        gui.hexEditSetting?.let { return HexInput.handleChar(gui, it, chr) }

        if (gui.numberEditSetting != null) return NumberInput.handleChar(gui, chr)

        if (gui.stringEditSetting != null) return StringInput.handleChar(gui, chr)

        if (gui.settingsModule != null) return false
        if (SearchBar.handleChar(chr)) return true
        if (chr.isLetterOrDigit()) return SearchBar.openWith(chr)
        return false
    }

    private fun expandedColorSetting(gui: ClickGUI): ColorSetting? =
        gui.settingsModule?.allSettings()?.filterIsInstance<ColorSetting>()?.firstOrNull { it.expanded }

}
