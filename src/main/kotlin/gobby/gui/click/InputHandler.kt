package gobby.gui.click

import gobby.gui.click.ClickGUITheme.ALPHA_BAR_H
import gobby.gui.click.ClickGUITheme.COLOR_PICKER_H
import gobby.gui.click.ClickGUITheme.HH
import gobby.gui.click.ClickGUITheme.HUE_BAR_H
import gobby.gui.click.ClickGUITheme.MH
import gobby.gui.click.ClickGUITheme.PAD
import gobby.gui.click.ClickGUITheme.PW
import gobby.gui.click.ClickGUITheme.SETTING_INDENT
import gobby.gui.click.ClickGUITheme.SH
import org.lwjgl.glfw.GLFW
import java.awt.Color

object InputHandler {

    fun handleMouseClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean {
        gui.hexEditSetting = null
        gui.numberEditSetting = null

        // Search bar click
        val sw = SearchBarRenderer.SEARCH_W
        val sh = SearchBarRenderer.SEARCH_H
        val sx = (gui.width - sw) / 2
        val sy = gui.height - 24
        if (mx in sx..(sx + sw) && my in sy..(sy + sh)) {
            gui.searchListening = true
            return true
        }
        gui.searchListening = false

        for (panel in gui.panels.reversed()) {
            val px = panel.x.toInt()
            val py = panel.y.toInt()
            if (mx !in px..(px + PW)) continue

            if (my in py..(py + HH)) {
                if (button == 1) {
                    panel.collapsed = !panel.collapsed
                } else {
                    panel.dragging = true
                    panel.dragOffsetX = mx - panel.x.toDouble()
                    panel.dragOffsetY = my - panel.y.toDouble()
                }
                return true
            }

            if (panel.collapsed) continue

            val modules = gui.getModulesForPanel(panel)
            var contentH = 0
            for (m in modules) {
                contentH += MH
                if (m.expanded) contentH += gui.settingsHeight(m)
            }
            val maxVisH = (gui.height - py - HH - 44).coerceAtLeast(30)
            val visH = contentH.coerceAtMost(maxVisH)

            if (my !in (py + HH)..(py + HH + visH)) continue

            var y = py + HH - panel.scrollOffset.toInt()
            for (module in modules) {
                if (my in y..(y + MH)) {
                    gui.listeningKeybind = null
                    if (button == 1 && module.allSettings().any { it.isVisible }) {
                        module.expanded = !module.expanded
                    } else if (module.toggled && !module.isAlwaysEnabled) {
                        module.enabled = !module.enabled
                        ConfigManager.save()
                    }
                    return true
                }
                y += MH

                if (module.expanded) {
                    for (setting in module.allSettings()) {
                        if (!setting.isVisible) continue
                        val sh = gui.settingHeight(setting)
                        if (my in y..(y + sh)) {
                            handleSettingClick(gui, panel.x.toInt(), y, mx, my, setting, button)
                            return true
                        }
                        y += sh
                    }
                }
            }
            return true
        }
        return false
    }

    private fun handleSettingClick(gui: ClickGUI, px: Int, y: Int, mx: Int, my: Int, setting: Setting<*>, button: Int) {
        when (setting) {
            is KeybindSetting -> {
                if (button == 1) {
                    setting.value = 0
                    gui.listeningKeybind = null
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
                    gui.numberInput = setting.value.toString()
                } else {
                    val slW = PW - SETTING_INDENT - PAD
                    val slX = px + SETTING_INDENT
                    if (mx in slX..(slX + slW)) {
                        gui.draggingSlider = setting
                        gui.sliderBaseX = slX
                        gui.sliderBaseW = slW
                        updateSlider(setting, mx, gui.sliderBaseX, gui.sliderBaseW)
                    }
                }
            }
            is SelectorSetting -> {
                if (button == 1) {
                    setting.value = if (setting.value <= 0) setting.options.lastIndex else setting.value - 1
                } else {
                    setting.value = if (setting.value >= setting.options.lastIndex) 0 else setting.value + 1
                }
                ConfigManager.save()
            }
            is ColorSetting -> handleColorSettingClick(gui, px, y, mx, my, setting, button)
            is ActionSetting -> setting.action()
        }
    }

    private fun handleColorSettingClick(gui: ClickGUI, px: Int, y: Int, mx: Int, my: Int, s: ColorSetting, button: Int) {
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
            val hue = if (s.cachedHue >= 0f) s.cachedHue else Color.RGBtoHSB(s.value.red, s.value.green, s.value.blue, null)[0]
            val alpha = s.value.alpha
            val rgb = Color.HSBtoRGB(hue, sat, bri)
            s.value = Color((rgb and 0x00FFFFFF) or (alpha shl 24), true)
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
            val hsb = Color.RGBtoHSB(s.value.red, s.value.green, s.value.blue, null)
            val alpha = s.value.alpha
            val rgb = Color.HSBtoRGB(hue, hsb[1], hsb[2])
            s.value = Color((rgb and 0x00FFFFFF) or (alpha shl 24), true)
            gui.draggingColorHue = s
            gui.colorPickerBaseX = padX
            gui.colorPickerBaseW = areaW
            ConfigManager.save()
            return
        }

        val alphaTop = hueBot + 4
        val alphaBot = alphaTop + ALPHA_BAR_H
        if (my in alphaTop until alphaBot && mx in padX until (padX + areaW)) {
            val alpha = ((mx - padX).toFloat() / areaW * 255).toInt().coerceIn(0, 255)
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
            return
        }
    }

    fun updateSlider(setting: NumberSetting, mx: Int, baseX: Int, baseW: Int) {
        val progress = ((mx - baseX).toFloat() / baseW).coerceIn(0f, 1f)
        var raw = (setting.min + (setting.max - setting.min) * progress).toInt()
        if (setting.step > 1) {
            raw = ((raw - setting.min + setting.step / 2) / setting.step) * setting.step + setting.min
        }
        setting.value = raw
        ConfigManager.save()
    }

    fun handleMouseDrag(gui: ClickGUI, currentX: Double, currentY: Double): Boolean {
        for (panel in gui.panels) {
            if (panel.dragging) {
                panel.x = (currentX - panel.dragOffsetX).toFloat()
                panel.y = (currentY - panel.dragOffsetY).toFloat()
                return true
            }
        }

        gui.draggingSlider?.let {
            updateSlider(it, currentX.toInt(), gui.sliderBaseX, gui.sliderBaseW)
            return true
        }

        gui.draggingColorSB?.let { s ->
            val sat = ((currentX.toInt() - gui.colorPickerBaseX).toFloat() / gui.colorPickerBaseW).coerceIn(0f, 1f)
            val bri = 1f - ((currentY.toInt() - gui.colorPickerSBTop).toFloat() / gui.colorPickerSBH).coerceIn(0f, 1f)
            val hue = if (s.cachedHue >= 0f) s.cachedHue else Color.RGBtoHSB(s.value.red, s.value.green, s.value.blue, null)[0]
            val alpha = s.value.alpha
            val rgb = Color.HSBtoRGB(hue, sat, bri)
            s.value = Color((rgb and 0x00FFFFFF) or (alpha shl 24), true)
            ConfigManager.save()
            return true
        }

        gui.draggingColorHue?.let { s ->
            val hue = ((currentX.toInt() - gui.colorPickerBaseX).toFloat() / gui.colorPickerBaseW).coerceIn(0f, 1f)
            s.cachedHue = hue
            val hsb = Color.RGBtoHSB(s.value.red, s.value.green, s.value.blue, null)
            val alpha = s.value.alpha
            val rgb = Color.HSBtoRGB(hue, hsb[1], hsb[2])
            s.value = Color((rgb and 0x00FFFFFF) or (alpha shl 24), true)
            ConfigManager.save()
            return true
        }

        gui.draggingColorAlpha?.let { s ->
            val alpha = ((currentX.toInt() - gui.colorPickerBaseX).toFloat() / gui.colorPickerBaseW * 255).toInt().coerceIn(0, 255)
            s.value = Color(s.value.red, s.value.green, s.value.blue, alpha)
            ConfigManager.save()
            return true
        }

        return false
    }

    fun handleMouseRelease(gui: ClickGUI) {
        for (panel in gui.panels) {
            if (panel.dragging) {
                panel.dragging = false
                ClickGUI.panelPositions[panel.category] = Pair(panel.x, panel.y)
            }
        }
        gui.draggingSlider = null
        gui.draggingColorSB = null
        gui.draggingColorHue = null
        gui.draggingColorAlpha = null
    }

    fun handleScroll(gui: ClickGUI, mouseX: Int, mouseY: Int, verticalAmount: Double): Boolean {
        for (panel in gui.panels.reversed()) {
            val px = panel.x.toInt()
            val py = panel.y.toInt()
            if (mouseX in px..(px + PW) && mouseY >= py + HH && !panel.collapsed) {
                panel.scrollOffset = (panel.scrollOffset - verticalAmount.toFloat() * 16f).coerceAtLeast(0f)
                return true
            }
        }
        return false
    }

    fun handleKeyPress(gui: ClickGUI, key: Int): Boolean {
        // Keybind listening
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

        // Hex input handling
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

        // Number input handling
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

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (gui.searchListening && gui.searchQuery.isNotEmpty()) {
                gui.searchQuery = ""
                gui.searchListening = false
                return true
            }
            gui.close()
            return true
        }
        if (gui.searchListening && key == GLFW.GLFW_KEY_BACKSPACE && gui.searchQuery.isNotEmpty()) {
            gui.searchQuery = gui.searchQuery.dropLast(1)
            return true
        }
        return false
    }

    fun handleCharTyped(gui: ClickGUI, chr: Char): Boolean {
        if (gui.suppressNextChar) {
            gui.suppressNextChar = false
            return true
        }
        if (gui.listeningKeybind != null) return true

        // Hex input for color picker
        gui.hexEditSetting?.let { s ->
            if (chr.uppercaseChar() in "0123456789ABCDEF" && gui.hexInput.length < 8) {
                gui.hexInput += chr.uppercaseChar()
                applyHexInput(gui, s)
                return true
            }
            return true
        }

        // Number input
        gui.numberEditSetting?.let {
            if (chr.isDigit() || (chr == '-' && gui.numberInput.isEmpty())) {
                gui.numberInput += chr
                return true
            }
            return true
        }

        if (gui.searchListening && (chr.isLetterOrDigit() || chr == ' ')) {
            gui.searchQuery += chr
            return true
        }
        if (!gui.searchListening && chr.isLetterOrDigit()) {
            gui.searchListening = true
            gui.searchQuery = chr.toString()
            return true
        }
        return false
    }

    private fun applyNumberInput(gui: ClickGUI, s: NumberSetting) {
        val parsed = gui.numberInput.toIntOrNull() ?: return
        s.value = parsed.coerceIn(s.min, s.max)
        ConfigManager.save()
    }

    private fun applyHexInput(gui: ClickGUI, s: ColorSetting) {
        try {
            if (gui.hexInput.length == 8) {
                val v = gui.hexInput.toLong(16).toInt()
                val r = (v ushr 24) and 0xFF
                val g = (v ushr 16) and 0xFF
                val b = (v ushr 8) and 0xFF
                val a = v and 0xFF
                s.value = Color(r, g, b, a)
                s.cachedHue = Color.RGBtoHSB(r, g, b, null)[0]
                ConfigManager.save()
            } else if (gui.hexInput.length == 6) {
                val rgb = gui.hexInput.toLong(16).toInt()
                val r = (rgb ushr 16) and 0xFF
                val g = (rgb ushr 8) and 0xFF
                val b = rgb and 0xFF
                s.value = Color(r, g, b, s.value.alpha)
                s.cachedHue = Color.RGBtoHSB(r, g, b, null)[0]
                ConfigManager.save()
            }
        } catch (_: Exception) {}
    }
}
