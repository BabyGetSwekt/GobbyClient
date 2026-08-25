package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils

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
        gui.searchSelectAll = false
        gui.stringEditSetting?.let { commitStringEdit(gui, it) }

        if (SearchComponent.handleClick(gui, mx, my)) return true
        if (SidebarComponent.handleClick(gui, mx, my)) return true

        val mod = gui.settingsModule
        return if (mod != null) {
            ModuleSettingsComponent.handleClick(gui, mod, mx, my, button)
        } else {
            ModuleGridComponent.handleClick(gui, mx, my, button)
        }
    }

    fun dispatchSettingClick(gui: ClickGUI, setting: Setting<*>, px: Int, y: Int, mx: Int, my: Int, button: Int): Boolean {
        gui.listeningKeybind = null
        when (setting) {
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
                    gui.numberEditSetting = setting
                    gui.numberInput = setting.display()
                } else {
                    val slW = PW - SETTING_INDENT - PAD
                    val slX = px + SETTING_INDENT
                    if (mx in slX..(slX + slW)) {
                        gui.draggingSlider = setting
                        gui.sliderBaseX = slX
                        gui.sliderBaseW = slW
                        updateSlider(setting, mx, slX, slW)
                    }
                }
            }
            is RangeSetting -> {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    val slW = PW - SETTING_INDENT - PAD
                    val slX = px + SETTING_INDENT
                    if (mx in slX..(slX + slW)) {
                        gui.draggingRange = setting
                        gui.sliderBaseX = slX
                        gui.sliderBaseW = slW
                        val lowX = slX + (slW * setting.progress(setting.value.start)).toInt()
                        val highX = slX + (slW * setting.progress(setting.value.endInclusive)).toInt()
                        gui.draggingRangeHigh = abs(mx - highX) <= abs(mx - lowX)
                        updateRange(setting, mx, slX, slW, gui.draggingRangeHigh)
                    }
                }
            }
            is StringSetting -> {
                gui.stringEditSetting = setting
                gui.stringInput = setting.value
                gui.stringCursor = setting.value.length
                gui.stringSelectAll = false
            }
            is SelectorSetting -> {
                setting.value = if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (setting.value <= 0) setting.options.lastIndex else setting.value - 1
                } else {
                    if (setting.value >= setting.options.lastIndex) 0 else setting.value + 1
                }
                ConfigManager.save()
            }
            is ColorSetting -> ColorPickerInput.handleClick(gui, px, y, mx, my, setting, button)
            is ActionSetting -> setting.action()
            is HudButton -> setting.onClick()
            is DropDownSetting -> {
                if (my < y + SH) {
                    setting.expanded = !setting.expanded
                }
            }
        }
        return true
    }

    private fun updateSlider(setting: NumberSetting, mx: Int, baseX: Int, baseW: Int) {
        setting.setFromProgress(((mx - baseX).toFloat() / baseW).coerceIn(0f, 1f))
        ConfigManager.save()
    }

    private fun commitStringEdit(gui: ClickGUI, setting: StringSetting) {
        setting.value = gui.stringInput.trim()
        gui.stringEditSetting = null
        gui.stringSelectAll = false
        setting.onCommit(setting.value)
        ConfigManager.save()
    }

    private fun pasteIntoString(gui: ClickGUI, setting: StringSetting) {
        val pasted = Utils.getClipboard().filter { isAllowedStringChar(it) }
        if (pasted.isNotEmpty()) insertInString(gui, setting, pasted)
    }

    private fun insertInString(gui: ClickGUI, setting: StringSetting, text: String) {
        if (gui.stringSelectAll) { gui.stringInput = ""; gui.stringCursor = 0; gui.stringSelectAll = false }
        val cursor = gui.stringCursor.coerceIn(0, gui.stringInput.length)
        val toAdd = text.take(setting.length - gui.stringInput.length)
        if (toAdd.isEmpty()) return
        gui.stringInput = gui.stringInput.substring(0, cursor) + toAdd + gui.stringInput.substring(cursor)
        gui.stringCursor = cursor + toAdd.length
    }

    private fun deleteInString(gui: ClickGUI, forward: Boolean) {
        if (gui.stringSelectAll) { gui.stringInput = ""; gui.stringCursor = 0; gui.stringSelectAll = false; return }
        val input = gui.stringInput
        val cursor = gui.stringCursor.coerceIn(0, input.length)
        if (forward) {
            if (cursor < input.length) gui.stringInput = input.removeRange(cursor, cursor + 1)
        } else if (cursor > 0) {
            gui.stringInput = input.removeRange(cursor - 1, cursor)
            gui.stringCursor = cursor - 1
        }
    }

    private fun isAllowedStringChar(chr: Char): Boolean = chr.isLetterOrDigit() || chr in "._:/-"

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
        val mod = gui.settingsModule
        return if (mod != null) {
            ModuleSettingsComponent.handleScroll(gui, mod, mouseX, mouseY, verticalAmount)
        } else {
            ModuleGridComponent.handleScroll(gui, mouseX, mouseY, verticalAmount)
        }
    }

    fun handleKeyPress(gui: ClickGUI, key: Int): Boolean {
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

        gui.hexEditSetting?.let {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                gui.hexEditSetting = null
                return true
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && gui.hexInput.isNotEmpty()) {
                gui.hexInput = gui.hexInput.dropLast(1)
                return true
            }
            return true
        }

        gui.numberEditSetting?.let { s ->
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                gui.numberEditSetting = null
                return true
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                applyNumberInput(gui, s)
                gui.numberEditSetting = null
                return true
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && gui.numberInput.isNotEmpty()) {
                gui.numberInput = gui.numberInput.dropLast(1)
                return true
            }
            return true
        }

        gui.stringEditSetting?.let { s ->
            when {
                key == GLFW.GLFW_KEY_ESCAPE -> { gui.stringEditSetting = null; gui.stringSelectAll = false }
                key == GLFW.GLFW_KEY_ENTER -> commitStringEdit(gui, s)
                key == GLFW.GLFW_KEY_A && isCtrlHeld() -> gui.stringSelectAll = gui.stringInput.isNotEmpty()
                key == GLFW.GLFW_KEY_C && isCtrlHeld() && gui.stringSelectAll -> Utils.setClipboard(gui.stringInput)
                key == GLFW.GLFW_KEY_V && isCtrlHeld() -> pasteIntoString(gui, s)
                key == GLFW.GLFW_KEY_LEFT -> { gui.stringSelectAll = false; gui.stringCursor = (gui.stringCursor - 1).coerceAtLeast(0) }
                key == GLFW.GLFW_KEY_RIGHT -> { gui.stringSelectAll = false; gui.stringCursor = (gui.stringCursor + 1).coerceAtMost(gui.stringInput.length) }
                key == GLFW.GLFW_KEY_HOME -> { gui.stringSelectAll = false; gui.stringCursor = 0 }
                key == GLFW.GLFW_KEY_END -> { gui.stringSelectAll = false; gui.stringCursor = gui.stringInput.length }
                key == GLFW.GLFW_KEY_DELETE -> deleteInString(gui, forward = true)
                key == GLFW.GLFW_KEY_BACKSPACE -> deleteInString(gui, forward = false)
            }
            return true
        }

        if (gui.searchFocused && key == GLFW.GLFW_KEY_A && isCtrlHeld()) {
            if (gui.searchQuery.isNotEmpty()) gui.searchSelectAll = true
            return true
        }

        if (gui.searchFocused && key == GLFW.GLFW_KEY_C && isCtrlHeld() && gui.searchSelectAll) {
            Utils.setClipboard(gui.searchQuery)
            return true
        }

        if (gui.searchFocused && key == GLFW.GLFW_KEY_V && isCtrlHeld()) {
            val pasted = Utils.getClipboard()
            if (pasted.isNotEmpty()) {
                gui.searchQuery = if (gui.searchSelectAll) pasted else gui.searchQuery + pasted
                gui.searchSelectAll = false
            }
            return true
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            gui.searchSelectAll = false
            if (gui.searchFocused && gui.searchQuery.isNotEmpty()) {
                gui.searchQuery = ""
                gui.searchFocused = false
                return true
            }
            if (gui.settingsModule != null) {
                gui.closeSettings()
                return true
            }
            gui.onClose()
            return true
        }

        if (gui.searchFocused && key == GLFW.GLFW_KEY_BACKSPACE) {
            if (gui.searchSelectAll) {
                gui.searchQuery = ""
                gui.searchSelectAll = false
                return true
            }
            if (gui.searchQuery.isNotEmpty()) {
                gui.searchQuery = gui.searchQuery.dropLast(1)
                return true
            }
        }
        return false
    }

    fun handleCharTyped(gui: ClickGUI, chr: Char): Boolean {
        if (gui.suppressNextChar) {
            gui.suppressNextChar = false
            return true
        }
        if (gui.listeningKeybind != null) return true

        gui.hexEditSetting?.let { s ->
            if (chr.uppercaseChar() in "0123456789ABCDEF" && gui.hexInput.length < 8) {
                gui.hexInput += chr.uppercaseChar()
                ColorPickerInput.applyHexInput(gui, s)
                return true
            }
            return true
        }

        gui.numberEditSetting?.let { s ->
            val allowDot = chr == '.' && s.decimals > 0 && '.' !in gui.numberInput
            if (chr.isDigit() || allowDot || (chr == '-' && gui.numberInput.isEmpty())) gui.numberInput += chr
            return true
        }

        gui.stringEditSetting?.let { s ->
            if (isAllowedStringChar(chr)) insertInString(gui, s, chr.toString())
            return true
        }

        if (gui.searchFocused && (chr.isLetterOrDigit() || chr == ' ')) {
            if (gui.searchSelectAll) {
                gui.searchQuery = chr.toString()
                gui.searchSelectAll = false
            } else {
                gui.searchQuery += chr
            }
            return true
        }
        if (!gui.searchFocused && chr.isLetterOrDigit()) {
            gui.searchFocused = true
            gui.searchQuery = chr.toString()
            return true
        }
        return false
    }

    private fun applyNumberInput(gui: ClickGUI, s: NumberSetting) {
        val parsed = gui.numberInput.toFloatOrNull() ?: return
        s.setSnapped(parsed)
        ConfigManager.save()
    }

    private fun isCtrlHeld(): Boolean {
        val handle = mc.window.handle()
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
    }
}
