package gobby.gui.click

import net.minecraft.client.gui.GuiGraphics
import java.awt.Color

object SettingRenderer {

    fun drawSettingRow(ctx: GuiGraphics, gui: ClickGUI, px: Int, y: Int, setting: Setting<*>, mx: Int, my: Int, clipTop: Int, clipBot: Int) {
        if (setting !is ColorSetting && setting !is DropDownSetting) {
            fill(ctx, px, y, PW, SH, cSettingBg)
        }

        when (setting) {
            is KeybindSetting -> drawKeybindSetting(ctx, gui, px, y, setting)
            is BooleanSetting -> drawBoolSetting(ctx, px, y, setting)
            is NumberSetting -> drawNumberSetting(ctx, gui, px, y, setting)
            is SelectorSetting -> drawSelectorSetting(ctx, px, y, setting)
            is ColorSetting -> drawColorSetting(ctx, gui, px, y, setting)
            is ActionSetting -> drawActionSetting(ctx, px, y, setting, mx, my, clipTop, clipBot)
            is HudButton -> drawHudButton(ctx, px, y, setting, mx, my, clipTop, clipBot)
            is DropDownSetting -> drawDropDownSetting(ctx, gui, px, y, setting, mx, my, clipTop, clipBot)
        }

        val inView = y + SH > clipTop && y < clipBot
        val hovered = inView && mx in px..(px + PW) && my in y.coerceAtLeast(clipTop)..(y + SH).coerceAtMost(clipBot)
        if (hovered && setting.description.isNotEmpty()) {
            gui.tooltipText = setting.description
            gui.tooltipX = px + PW + 8
            gui.tooltipY = y
        }
    }

    private fun drawKeybindSetting(ctx: GuiGraphics, gui: ClickGUI, px: Int, y: Int, s: KeybindSetting) {
        val fh = (tr.lineHeight * SETTING_SCALE).toInt()
        drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val isListening = gui.listeningKeybind == s
        val keyName = if (isListening) "..." else s.getKeyName()
        val keyW = textWSmall(keyName)

        val boxPad = 3
        val boxW = keyW + boxPad * 2
        val boxH = SH - 4
        val boxX = px + PW - PAD - boxW
        val boxY = y + 2

        if (isListening) {
            fill(ctx, boxX, boxY, boxW, boxH, cAccent)
            drawTextSmall(ctx, boxX + boxPad, boxY + (boxH - fh) / 2, keyName, cTextBright)
        } else {
            fill(ctx, boxX, boxY, boxW, boxH, cKeyBox)
            drawBorder(ctx, boxX, boxY, boxW, boxH, cKeyBoxBorder)
            drawTextSmall(ctx, boxX + boxPad, boxY + (boxH - fh) / 2, keyName, cText)
        }
    }

    private fun drawBoolSetting(ctx: GuiGraphics, px: Int, y: Int, s: BooleanSetting) {
        val fh = (tr.lineHeight * SETTING_SCALE).toInt()
        drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val tx = px + PW - TOGGLE_W - PAD
        val ty = y + (SH - TOGGLE_H) / 2
        fill(ctx, tx, ty, TOGGLE_W, TOGGLE_H, if (s.value) cToggleOn else cToggleOff)

        val knobX = if (s.value) tx + TOGGLE_W - KNOB_W - 1 else tx + 1
        val knobY = ty + (TOGGLE_H - KNOB_H) / 2
        fill(ctx, knobX, knobY, KNOB_W, KNOB_H, cKnob)
    }

    private fun drawNumberSetting(ctx: GuiGraphics, gui: ClickGUI, px: Int, y: Int, s: NumberSetting) {
        val fh = (tr.lineHeight * SETTING_SCALE).toInt()
        val textY = y + (SH - SLIDER_H - 2 - fh) / 2
        drawTextSmall(ctx, px + SETTING_INDENT, textY, s.name, cTextGray)

        val isEditing = gui.numberEditSetting == s
        val valStr = if (isEditing) gui.numberInput else s.value.toString()
        val valCol = if (isEditing) cTextBright else cText
        val valW = textWSmall(valStr)
        drawTextSmall(ctx, px + PW - PAD - valW, textY, valStr, valCol)

        if (isEditing && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val curX = px + PW - PAD
            fill(ctx, curX, textY, 1, fh, cAccent)
        }

        val slW = PW - SETTING_INDENT - PAD
        val slX = px + SETTING_INDENT
        val slY = y + SH - SLIDER_H - 2
        val progress = (s.value - s.min).toFloat() / (s.max - s.min).coerceAtLeast(1)
        val fillW = (slW * progress).toInt()
        fill(ctx, slX, slY, slW, SLIDER_H, cSliderTrack)
        fill(ctx, slX, slY, fillW, SLIDER_H, cSliderFill)
    }

    private fun drawSelectorSetting(ctx: GuiGraphics, px: Int, y: Int, s: SelectorSetting) {
        val fh = (tr.lineHeight * SETTING_SCALE).toInt()
        drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val opt = s.options.getOrElse(s.value) { "?" }
        val display = "< $opt >"
        val optW = textWSmall(display)
        drawTextSmall(ctx, px + PW - PAD - optW, y + (SH - fh) / 2, display, cAccent)
    }

    private fun drawColorSetting(ctx: GuiGraphics, gui: ClickGUI, px: Int, y: Int, s: ColorSetting) {
        fill(ctx, px, y, PW, SH, cSettingBg)
        val fh = (tr.lineHeight * SETTING_SCALE).toInt()
        drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val swatchSize = 10
        val cx = px + PW - PAD - swatchSize
        val cy = y + (SH - swatchSize) / 2
        fill(ctx, cx - 1, cy - 1, swatchSize + 2, swatchSize + 2, cBorder)
        fill(ctx, cx, cy, swatchSize, swatchSize, s.value.rgb or (0xFF shl 24))

        if (!s.expanded) return

        val pickerY = y + SH
        fill(ctx, px, pickerY, PW, COLOR_PICKER_H, cPickerBg)

        val padX = px + SETTING_INDENT
        val areaW = PW - SETTING_INDENT - PAD
        val hsb = Color.RGBtoHSB(s.value.red, s.value.green, s.value.blue, null)
        if (s.cachedHue < 0f) s.cachedHue = hsb[0]
        val hue = s.cachedHue
        val alpha = s.value.alpha

        val sbH = COLOR_PICKER_H - HUE_BAR_H - ALPHA_BAR_H - 38
        val sbTop = pickerY + 3
        val cols = areaW / SB_SIZE
        val rows = sbH / SB_SIZE

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val sat = col.toFloat() / (cols - 1)
                val bri = 1f - row.toFloat() / (rows - 1)
                val c = Color(Color.HSBtoRGB(hue, sat, bri))
                fill(ctx, padX + col * SB_SIZE, sbTop + row * SB_SIZE, SB_SIZE, SB_SIZE, c.rgb)
            }
        }

        drawBorder(ctx, padX - 1, sbTop - 1, areaW + 2, sbH + 2, cBorder)

        val cursorCol = (hsb[1] * (cols - 1)).toInt().coerceIn(0, cols - 1)
        val cursorRow = ((1f - hsb[2]) * (rows - 1)).toInt().coerceIn(0, rows - 1)
        val crX = padX + cursorCol * SB_SIZE
        val crY = sbTop + cursorRow * SB_SIZE
        fill(ctx, crX - 2, crY, 5, 1, cCrosshairDark)
        fill(ctx, crX, crY - 2, 1, 5, cCrosshairDark)
        fill(ctx, crX - 1, crY, 3, 1, cCrosshairLight)
        fill(ctx, crX, crY - 1, 1, 3, cCrosshairLight)

        val hueTop = sbTop + sbH + 4
        for (i in 0 until areaW) {
            val h = i.toFloat() / areaW * 0.999f
            val c = Color(Color.HSBtoRGB(h, 1f, 1f))
            fill(ctx, padX + i, hueTop, 1, HUE_BAR_H, c.rgb)
        }
        drawBorder(ctx, padX - 1, hueTop - 1, areaW + 2, HUE_BAR_H + 2, cBorder)

        val hueX = padX + (hue * (areaW - 2)).toInt()
        fill(ctx, hueX - 1, hueTop - 2, 3, HUE_BAR_H + 4, cHueIndicator)

        val alphaTop = hueTop + HUE_BAR_H + 4
        val baseColor = Color(Color.HSBtoRGB(hue, hsb[1], hsb[2]))
        for (i in 0 until areaW) {
            val a = (i.toFloat() / areaW * 255).toInt()
            val c = Color(baseColor.red, baseColor.green, baseColor.blue, a)
            fill(ctx, padX + i, alphaTop, 1, ALPHA_BAR_H, c.rgb)
        }
        drawBorder(ctx, padX - 1, alphaTop - 1, areaW + 2, ALPHA_BAR_H + 2, cBorder)

        val alphaLabel = "A"
        drawTextSmall(ctx, padX - textWSmall(alphaLabel) - 2, alphaTop + (ALPHA_BAR_H - fh) / 2, alphaLabel, cTextDark, false)

        val alphaX = padX + (alpha / 255f * (areaW - 2)).toInt()
        fill(ctx, alphaX - 1, alphaTop - 2, 3, ALPHA_BAR_H + 4, cHueIndicator)

        val hexTop = alphaTop + ALPHA_BAR_H + 5
        val hexH = 14
        val hexW = areaW
        fill(ctx, padX, hexTop, hexW, hexH, cHexBoxBg)
        drawBorder(ctx, padX, hexTop, hexW, hexH, if (gui.hexEditSetting == s) cAccent else cBorder)

        val isEditingHex = gui.hexEditSetting == s
        val c = s.value
        val hexStr = if (isEditingHex) "#${gui.hexInput}" else String.format("#%02X%02X%02X%02X", c.red, c.green, c.blue, c.alpha)
        val hexCol = if (isEditingHex) cTextBright else cText
        val hexTW = textWSmall(hexStr)
        drawTextSmall(ctx, padX + (hexW - hexTW) / 2, hexTop + (hexH - fh) / 2, hexStr, hexCol)

        if (isEditingHex && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val curX = padX + (hexW - hexTW) / 2 + textWSmall("#${gui.hexInput}")
            fill(ctx, curX, hexTop + 3, 1, hexH - 6, cAccent)
        }
    }

    private fun drawDropDownSetting(ctx: GuiGraphics, gui: ClickGUI, px: Int, y: Int, s: DropDownSetting, mx: Int, my: Int, clipTop: Int, clipBot: Int) {
        fill(ctx, px, y, PW, SH, cSettingBg)
        val fh = (tr.lineHeight * SETTING_SCALE).toInt()
        drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val arrowX = px + PW - PAD - 5
        val arrowY = y + (SH - 3) / 2
        if (s.expanded) {
            fill(ctx, arrowX + 2, arrowY, 1, 1, cAccent)
            fill(ctx, arrowX + 1, arrowY + 1, 3, 1, cAccent)
            fill(ctx, arrowX, arrowY + 2, 5, 1, cAccent)
        } else {
            fill(ctx, arrowX, arrowY, 5, 1, cAccent)
            fill(ctx, arrowX + 1, arrowY + 1, 3, 1, cAccent)
            fill(ctx, arrowX + 2, arrowY + 2, 1, 1, cAccent)
        }

        if (!s.expanded) return

        var childY = y + SH
        for (child in s.children) {
            if (!child.isVisible) continue
            drawSettingRow(ctx, gui, px, childY, child, mx, my, clipTop, clipBot)
            childY += ModuleSettingsComponent.settingHeight(child)
        }
    }

    private fun drawActionSetting(ctx: GuiGraphics, px: Int, y: Int, s: ActionSetting, mx: Int, my: Int, clipTop: Int, clipBot: Int) {
        val hovered = mx in px..(px + PW) && my in y.coerceAtLeast(clipTop)..(y + SH + y).coerceAtMost(clipBot)
        val nameW = textWSmall(s.name)
        val fh = (tr.lineHeight * SETTING_SCALE).toInt()
        val textX = px + (PW - nameW) / 2
        val textY = y + (SH - fh) / 2

        drawTextSmall(ctx, textX, textY, s.name, if (hovered) cTextBright else cAccent)

        if (hovered) {
            fill(ctx, textX, textY + fh, nameW, 1, cAccent)
        }
    }

    private fun drawHudButton(ctx: GuiGraphics, px: Int, y: Int, s: HudButton, mx: Int, my: Int, clipTop: Int, clipBot: Int) {
        val fh = (tr.lineHeight * SETTING_SCALE).toInt()
        drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val hovered = mx in px..(px + PW) && my in y.coerceAtLeast(clipTop)..(y + SH).coerceAtMost(clipBot)
        val col = if (hovered) cTextBright else cAccent

        val iconSize = 8
        val ix = px + PW - iconSize - PAD
        val iy = y + (SH - iconSize) / 2

        fill(ctx, ix, iy, iconSize, 1, col)                    // top
        fill(ctx, ix, iy + iconSize - 1, iconSize, 1, col)     // bottom
        fill(ctx, ix, iy, 1, iconSize, col)                    // left
        fill(ctx, ix + iconSize - 1, iy, 1, iconSize, col)     // right
        fill(ctx, ix + 1, iy + 1, iconSize - 2, 2, col)
    }
}
