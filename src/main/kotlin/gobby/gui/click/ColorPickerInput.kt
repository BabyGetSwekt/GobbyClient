package gobby.gui.click

import org.lwjgl.glfw.GLFW
import java.awt.Color

object ColorPickerInput {

    private const val PICKER_CHROME_H = 38
    private const val SB_TOP_GAP = 3
    private const val BAR_GAP = 4
    private const val HEX_GAP = 5
    private const val HEX_ROW_H = 14
    private const val HEX_RGBA_LENGTH = 8
    private const val HEX_RGB_LENGTH = 6
    private const val MIN_ALPHA = 1
    private const val MAX_ALPHA = 255
    private const val BYTE_MASK = 0xFF
    private const val RGB_MASK = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24

    fun handleClick(gui: ClickGUI, px: Int, y: Int, mx: Int, my: Int, s: ColorSetting, button: Int) {
        if (my < y + SH) {
            toggleExpanded(gui, s, button)
            return
        }
        if (!s.expanded) return

        val padX = px + SETTING_INDENT
        val areaW = PW - SETTING_INDENT - PAD
        if (mx !in padX until (padX + areaW)) return

        val sbH = COLOR_PICKER_H - HUE_BAR_H - ALPHA_BAR_H - PICKER_CHROME_H
        val sbTop = y + SH + SB_TOP_GAP
        val sbBot = sbTop + sbH
        val hueTop = sbBot + BAR_GAP
        val hueBot = hueTop + HUE_BAR_H
        val alphaTop = hueBot + BAR_GAP
        val alphaBot = alphaTop + ALPHA_BAR_H
        val hexTop = alphaBot + HEX_GAP

        when (my) {
            in sbTop until sbBot -> beginSaturationDrag(gui, s, mx, my, padX, areaW, sbTop, sbH)
            in hueTop until hueBot -> beginHueDrag(gui, s, mx, padX, areaW)
            in alphaTop until alphaBot -> beginAlphaDrag(gui, s, mx, padX, areaW)
            in hexTop until (hexTop + HEX_ROW_H) -> beginHexEdit(gui, s)
        }
    }

    private fun toggleExpanded(gui: ClickGUI, s: ColorSetting, button: Int) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            s.expanded = !s.expanded
            if (!s.expanded) gui.hexEditSetting = null
        } else if (!s.expanded) {
            s.expanded = true
        }
    }

    private fun beginSaturationDrag(gui: ClickGUI, s: ColorSetting, mx: Int, my: Int, padX: Int, areaW: Int, sbTop: Int, sbH: Int) {
        val sat = ((mx - padX).toFloat() / areaW).coerceIn(0f, 1f)
        val bri = 1f - ((my - sbTop).toFloat() / sbH).coerceIn(0f, 1f)
        s.applyHsbPreserveAlpha(s.effectiveHue, sat, bri)
        gui.draggingColorSB = s
        gui.colorPickerSBTop = sbTop
        gui.colorPickerSBH = sbH
        rememberPickerBounds(gui, padX, areaW)
        ConfigManager.save()
    }

    private fun beginHueDrag(gui: ClickGUI, s: ColorSetting, mx: Int, padX: Int, areaW: Int) {
        applyHue(s, ((mx - padX).toFloat() / areaW).coerceIn(0f, 1f))
        gui.draggingColorHue = s
        rememberPickerBounds(gui, padX, areaW)
        ConfigManager.save()
    }

    private fun beginAlphaDrag(gui: ClickGUI, s: ColorSetting, mx: Int, padX: Int, areaW: Int) {
        s.value = s.value.withAlpha(scaleToAlpha((mx - padX).toFloat() / areaW))
        gui.draggingColorAlpha = s
        rememberPickerBounds(gui, padX, areaW)
        ConfigManager.save()
    }

    private fun beginHexEdit(gui: ClickGUI, s: ColorSetting) {
        gui.hexEditSetting = s
        gui.hexInput = String.format("%02X%02X%02X%02X", s.value.red, s.value.green, s.value.blue, s.value.alpha)
    }

    private fun rememberPickerBounds(gui: ClickGUI, padX: Int, areaW: Int) {
        gui.colorPickerBaseX = padX
        gui.colorPickerBaseW = areaW
    }

    fun handleDrag(gui: ClickGUI, currentX: Double, currentY: Double): Boolean {
        gui.draggingColorSB?.let { s ->
            s.applyHsbPreserveAlpha(s.effectiveHue, horizontalFraction(gui, currentX), verticalBrightness(gui, currentY))
            ConfigManager.save()
            return true
        }
        gui.draggingColorHue?.let { s ->
            applyHue(s, horizontalFraction(gui, currentX))
            ConfigManager.save()
            return true
        }
        gui.draggingColorAlpha?.let { s ->
            s.value = s.value.withAlpha(scaleToAlpha(rawHorizontalFraction(gui, currentX)))
            ConfigManager.save()
            return true
        }
        return false
    }

    fun clearDragging(gui: ClickGUI) {
        gui.draggingColorSB = null
        gui.draggingColorHue = null
        gui.draggingColorAlpha = null
    }

    fun applyHexInput(gui: ClickGUI, s: ColorSetting) {
        val packed = gui.hexInput.toLongOrNull(16)?.toInt() ?: return
        when (gui.hexInput.length) {
            HEX_RGBA_LENGTH -> s.applyRgb(packed.byteAt(3), packed.byteAt(2), packed.byteAt(1), packed.byteAt(0).coerceAtLeast(MIN_ALPHA))
            HEX_RGB_LENGTH -> s.applyRgb(packed.byteAt(2), packed.byteAt(1), packed.byteAt(0), s.value.alpha)
        }
    }

    private fun applyHue(s: ColorSetting, hue: Float) {
        s.cachedHue = hue
        val hsb = s.toHsb()
        s.applyHsbPreserveAlpha(hue, hsb[1], hsb[2])
    }

    private fun rawHorizontalFraction(gui: ClickGUI, x: Double): Float =
        (x.toInt() - gui.colorPickerBaseX).toFloat() / gui.colorPickerBaseW

    private fun horizontalFraction(gui: ClickGUI, x: Double): Float = rawHorizontalFraction(gui, x).coerceIn(0f, 1f)

    private fun verticalBrightness(gui: ClickGUI, y: Double): Float =
        1f - ((y.toInt() - gui.colorPickerSBTop).toFloat() / gui.colorPickerSBH).coerceIn(0f, 1f)

    private fun scaleToAlpha(fraction: Float): Int = (fraction * MAX_ALPHA).toInt().coerceIn(MIN_ALPHA, MAX_ALPHA)

    private fun Int.byteAt(index: Int): Int = (this ushr (index * Byte.SIZE_BITS)) and BYTE_MASK

    private fun Color.withAlpha(alpha: Int): Color = Color(red, green, blue, alpha)

    private fun ColorSetting.toHsb(): FloatArray = Color.RGBtoHSB(value.red, value.green, value.blue, null)

    private val ColorSetting.effectiveHue: Float
        get() = if (cachedHue >= 0f) cachedHue else toHsb()[0]

    private fun ColorSetting.applyHsbPreserveAlpha(hue: Float, sat: Float, bri: Float) {
        val rgb = Color.HSBtoRGB(hue, sat, bri)
        value = Color((rgb and RGB_MASK) or (value.alpha shl ALPHA_SHIFT), true)
    }

    private fun ColorSetting.applyRgb(r: Int, g: Int, b: Int, a: Int) {
        value = Color(r, g, b, a)
        cachedHue = Color.RGBtoHSB(r, g, b, null)[0]
        ConfigManager.save()
    }
}
