package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils

import org.lwjgl.glfw.GLFW
import java.awt.Color
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
                if (button == 1) {
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
                if (button == 1) {
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
                if (button == 0) {
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
                setting.value = if (button == 1) {
                    if (setting.value <= 0) setting.options.lastIndex else setting.value - 1
                } else {
                    if (setting.value >= setting.options.lastIndex) 0 else setting.value + 1
                }
                ConfigManager.save()
            }
            is ColorSetting -> handleColorClick(gui, px, y, mx, my, setting, button)
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

    private fun handleColorClick(gui: ClickGUI, px: Int, y: Int, mx: Int, my: Int, s: ColorSetting, button: Int) {
        if (my < y + SH) {
            if (button == 1) {
                s.expanded = !s.expanded
                if (!s.expanded) gui.hexEditSetting = null
            } else if (!s.expanded) {
                s.expanded = true
            }
            return
        }
        if (!s.expanded) return

        val pickerY = y + SH
        val padX = px + SETTING_INDENT
        val areaW = PW - SETTING_INDENT - PAD
        val sbH = COLOR_PICKER_H - HUE_BAR_H - ALPHA_BAR_H - 38
        val sbTop = pickerY + 3
        val sbBot = sbTop + sbH

        if (my in sbTop until sbBot && mx in padX until (padX + areaW)) {
            val sat = ((mx - padX).toFloat() / areaW).coerceIn(0f, 1f)
            val bri = 1f - ((my - sbTop).toFloat() / sbH).coerceIn(0f, 1f)
            s.applyHsbPreserveAlpha(s.effectiveHue, sat, bri)
            gui.draggingColorSB = s
            gui.colorPickerBaseX = padX
            gui.colorPickerBaseW = areaW
            gui.colorPickerSBTop = sbTop
            gui.colorPickerSBH = sbH
            ConfigManager.save()
            return
        }

        val hueTop = sbBot + 4
        val hueBot = hueTop + HUE_BAR_H
        if (my in hueTop until hueBot && mx in padX until (padX + areaW)) {
            val hue = ((mx - padX).toFloat() / areaW).coerceIn(0f, 1f)
            s.cachedHue = hue
            val hsb = s.toHsb()
            s.applyHsbPreserveAlpha(hue, hsb[1], hsb[2])
            gui.draggingColorHue = s
            gui.colorPickerBaseX = padX
            gui.colorPickerBaseW = areaW
            ConfigManager.save()
            return
        }

        val alphaTop = hueBot + 4
        val alphaBot = alphaTop + ALPHA_BAR_H
        if (my in alphaTop until alphaBot && mx in padX until (padX + areaW)) {
            val alpha = ((mx - padX).toFloat() / areaW * 255).toInt().coerceIn(1, 255)
            s.value = Color(s.value.red, s.value.green, s.value.blue, alpha)
            gui.draggingColorAlpha = s
            gui.colorPickerBaseX = padX
            gui.colorPickerBaseW = areaW
            ConfigManager.save()
            return
        }

        val hexTop = alphaBot + 5
        val hexBot = hexTop + 14
        if (my in hexTop until hexBot && mx in padX until (padX + areaW)) {
            gui.hexEditSetting = s
            gui.hexInput = String.format("%02X%02X%02X%02X", s.value.red, s.value.green, s.value.blue, s.value.alpha)
        }
    }

    private fun ColorSetting.toHsb(): FloatArray =
        Color.RGBtoHSB(value.red, value.green, value.blue, null)

    private val ColorSetting.effectiveHue: Float
        get() = if (cachedHue >= 0f) cachedHue else toHsb()[0]

    private fun ColorSetting.applyHsbPreserveAlpha(hue: Float, sat: Float, bri: Float) {
        val rgb = Color.HSBtoRGB(hue, sat, bri)
        value = Color((rgb and 0x00FFFFFF) or (value.alpha shl 24), true)
    }

    private fun ColorSetting.applyRgb(r: Int, g: Int, b: Int, a: Int) {
        value = Color(r, g, b, a)
        cachedHue = Color.RGBtoHSB(r, g, b, null)[0]
        ConfigManager.save()
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
        gui.draggingColorSB?.let { s ->
            val sat = ((currentX.toInt() - gui.colorPickerBaseX).toFloat() / gui.colorPickerBaseW).coerceIn(0f, 1f)
            val bri = 1f - ((currentY.toInt() - gui.colorPickerSBTop).toFloat() / gui.colorPickerSBH).coerceIn(0f, 1f)
            s.applyHsbPreserveAlpha(s.effectiveHue, sat, bri)
            ConfigManager.save()
            return true
        }
        gui.draggingColorHue?.let { s ->
            val hue = ((currentX.toInt() - gui.colorPickerBaseX).toFloat() / gui.colorPickerBaseW).coerceIn(0f, 1f)
            s.cachedHue = hue
            val hsb = s.toHsb()
            s.applyHsbPreserveAlpha(hue, hsb[1], hsb[2])
            ConfigManager.save()
            return true
        }
        gui.draggingColorAlpha?.let { s ->
            val alpha = ((currentX.toInt() - gui.colorPickerBaseX).toFloat() / gui.colorPickerBaseW * 255).toInt().coerceIn(1, 255)
            s.value = Color(s.value.red, s.value.green, s.value.blue, alpha)
            ConfigManager.save()
            return true
        }
        return false
    }

    fun handleMouseRelease(gui: ClickGUI) {
        gui.draggingSlider = null
        gui.draggingRange = null
        gui.draggingColorSB = null
        gui.draggingColorHue = null
        gui.draggingColorAlpha = null
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
                applyHexInput(gui, s)
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

    private fun applyHexInput(gui: ClickGUI, s: ColorSetting) {
        try {
            val v = gui.hexInput.toLong(16).toInt()
            when (gui.hexInput.length) {
                8 -> s.applyRgb((v ushr 24) and 0xFF, (v ushr 16) and 0xFF, (v ushr 8) and 0xFF, (v and 0xFF).coerceAtLeast(1))
                6 -> s.applyRgb((v ushr 16) and 0xFF, (v ushr 8) and 0xFF, v and 0xFF, s.value.alpha)
            }
        } catch (_: Exception) {}
    }
}
