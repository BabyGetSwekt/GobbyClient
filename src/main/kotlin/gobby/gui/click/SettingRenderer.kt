package gobby.gui.click

import gobby.gui.click.ClickGUITheme.ALPHA_BAR_H
import gobby.gui.click.ClickGUITheme.COLOR_PICKER_H
import gobby.gui.click.ClickGUITheme.HUE_BAR_H
import gobby.gui.click.ClickGUITheme.KNOB_H
import gobby.gui.click.ClickGUITheme.KNOB_W
import gobby.gui.click.ClickGUITheme.PAD
import gobby.gui.click.ClickGUITheme.PW
import gobby.gui.click.ClickGUITheme.SB_SIZE
import gobby.gui.click.ClickGUITheme.SETTING_INDENT
import gobby.gui.click.ClickGUITheme.SETTING_SCALE
import gobby.gui.click.ClickGUITheme.SH
import gobby.gui.click.ClickGUITheme.SLIDER_H
import gobby.gui.click.ClickGUITheme.TOGGLE_H
import gobby.gui.click.ClickGUITheme.TOGGLE_W
import gobby.gui.click.ClickGUITheme.cAccent
import gobby.gui.click.ClickGUITheme.cBorder
import gobby.gui.click.ClickGUITheme.cCrosshairDark
import gobby.gui.click.ClickGUITheme.cCrosshairLight
import gobby.gui.click.ClickGUITheme.cHexBoxBg
import gobby.gui.click.ClickGUITheme.cHueIndicator
import gobby.gui.click.ClickGUITheme.cKeyBox
import gobby.gui.click.ClickGUITheme.cKeyBoxBorder
import gobby.gui.click.ClickGUITheme.cKnob
import gobby.gui.click.ClickGUITheme.cPickerBg
import gobby.gui.click.ClickGUITheme.cSettingBg
import gobby.gui.click.ClickGUITheme.cSliderFill
import gobby.gui.click.ClickGUITheme.cSliderTrack
import gobby.gui.click.ClickGUITheme.cText
import gobby.gui.click.ClickGUITheme.cTextBright
import gobby.gui.click.ClickGUITheme.cTextDark
import gobby.gui.click.ClickGUITheme.cTextGray
import gobby.gui.click.ClickGUITheme.cToggleOff
import gobby.gui.click.ClickGUITheme.cToggleOn
import gobby.gui.click.ClickGUITheme.tr
import net.minecraft.client.gui.DrawContext
import java.awt.Color

object SettingRenderer {

    fun drawSettingRow(ctx: DrawContext, gui: ClickGUI, px: Int, y: Int, setting: Setting<*>, mx: Int, my: Int, clipTop: Int, clipBot: Int) {
        if (setting !is ColorSetting) {
            ClickGUITheme.fill(ctx, px, y, PW, SH, cSettingBg)
        }

        when (setting) {
            is KeybindSetting -> drawKeybindSetting(ctx, gui, px, y, setting)
            is BooleanSetting -> drawBoolSetting(ctx, px, y, setting)
            is NumberSetting -> drawNumberSetting(ctx, gui, px, y, setting)
            is SelectorSetting -> drawSelectorSetting(ctx, px, y, setting)
            is ColorSetting -> drawColorSetting(ctx, gui, px, y, setting)
            is ActionSetting -> drawActionSetting(ctx, px, y, setting, mx, my, clipTop, clipBot)
        }

        // Setting tooltip on hover
        val inView = y + SH > clipTop && y < clipBot
        val hovered = inView && mx in px..(px + PW) && my in y.coerceAtLeast(clipTop)..(y + SH).coerceAtMost(clipBot)
        if (hovered && setting.description.isNotEmpty()) {
            gui.tooltipText = setting.description
            gui.tooltipX = px + PW + 8
            gui.tooltipY = y
        }
    }

    private fun drawKeybindSetting(ctx: DrawContext, gui: ClickGUI, px: Int, y: Int, s: KeybindSetting) {
        val fh = (tr.fontHeight * SETTING_SCALE).toInt()
        ClickGUITheme.drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val isListening = gui.listeningKeybind == s
        val keyName = if (isListening) "..." else s.getKeyName()
        val keyW = ClickGUITheme.textWSmall(keyName)

        val boxPad = 3
        val boxW = keyW + boxPad * 2
        val boxH = SH - 4
        val boxX = px + PW - PAD - boxW
        val boxY = y + 2

        if (isListening) {
            ClickGUITheme.fill(ctx, boxX, boxY, boxW, boxH, cAccent)
            ClickGUITheme.drawTextSmall(ctx, boxX + boxPad, boxY + (boxH - fh) / 2, keyName, cTextBright)
        } else {
            ClickGUITheme.fill(ctx, boxX, boxY, boxW, boxH, cKeyBox)
            ClickGUITheme.drawBorder(ctx, boxX, boxY, boxW, boxH, cKeyBoxBorder)
            ClickGUITheme.drawTextSmall(ctx, boxX + boxPad, boxY + (boxH - fh) / 2, keyName, cText)
        }
    }

    private fun drawBoolSetting(ctx: DrawContext, px: Int, y: Int, s: BooleanSetting) {
        val fh = (tr.fontHeight * SETTING_SCALE).toInt()
        ClickGUITheme.drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val tx = px + PW - TOGGLE_W - PAD
        val ty = y + (SH - TOGGLE_H) / 2
        ClickGUITheme.fill(ctx, tx, ty, TOGGLE_W, TOGGLE_H, if (s.value) cToggleOn else cToggleOff)

        val knobX = if (s.value) tx + TOGGLE_W - KNOB_W - 1 else tx + 1
        val knobY = ty + (TOGGLE_H - KNOB_H) / 2
        ClickGUITheme.fill(ctx, knobX, knobY, KNOB_W, KNOB_H, cKnob)
    }

    private fun drawNumberSetting(ctx: DrawContext, gui: ClickGUI, px: Int, y: Int, s: NumberSetting) {
        val fh = (tr.fontHeight * SETTING_SCALE).toInt()
        val textY = y + (SH - SLIDER_H - 2 - fh) / 2
        ClickGUITheme.drawTextSmall(ctx, px + SETTING_INDENT, textY, s.name, cTextGray)

        val isEditing = gui.numberEditSetting == s
        val valStr = if (isEditing) gui.numberInput else s.value.toString()
        val valCol = if (isEditing) cTextBright else cText
        val valW = ClickGUITheme.textWSmall(valStr)
        ClickGUITheme.drawTextSmall(ctx, px + PW - PAD - valW, textY, valStr, valCol)

        if (isEditing && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val curX = px + PW - PAD
            ClickGUITheme.fill(ctx, curX, textY, 1, fh, cAccent)
        }

        val slW = PW - SETTING_INDENT - PAD
        val slX = px + SETTING_INDENT
        val slY = y + SH - SLIDER_H - 2
        val progress = (s.value - s.min).toFloat() / (s.max - s.min).coerceAtLeast(1)
        val fillW = (slW * progress).toInt()
        ClickGUITheme.fill(ctx, slX, slY, slW, SLIDER_H, cSliderTrack)
        ClickGUITheme.fill(ctx, slX, slY, fillW, SLIDER_H, cSliderFill)
    }

    private fun drawSelectorSetting(ctx: DrawContext, px: Int, y: Int, s: SelectorSetting) {
        val fh = (tr.fontHeight * SETTING_SCALE).toInt()
        ClickGUITheme.drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        val opt = s.options.getOrElse(s.value) { "?" }
        val display = "< $opt >"
        val optW = ClickGUITheme.textWSmall(display)
        ClickGUITheme.drawTextSmall(ctx, px + PW - PAD - optW, y + (SH - fh) / 2, display, cAccent)
    }

    private fun drawColorSetting(ctx: DrawContext, gui: ClickGUI, px: Int, y: Int, s: ColorSetting) {
        ClickGUITheme.fill(ctx, px, y, PW, SH, cSettingBg)
        val fh = (tr.fontHeight * SETTING_SCALE).toInt()
        ClickGUITheme.drawTextSmall(ctx, px + SETTING_INDENT, y + (SH - fh) / 2, s.name, cTextGray)

        // Color swatch with border
        val swatchSize = 10
        val cx = px + PW - PAD - swatchSize
        val cy = y + (SH - swatchSize) / 2
        ClickGUITheme.fill(ctx, cx - 1, cy - 1, swatchSize + 2, swatchSize + 2, cBorder)
        ClickGUITheme.fill(ctx, cx, cy, swatchSize, swatchSize, s.value.rgb or (0xFF shl 24))

        if (!s.expanded) return

        // Expanded color picker
        val pickerY = y + SH
        ClickGUITheme.fill(ctx, px, pickerY, PW, COLOR_PICKER_H, cPickerBg)

        val padX = px + SETTING_INDENT
        val areaW = PW - SETTING_INDENT - PAD
        val hsb = Color.RGBtoHSB(s.value.red, s.value.green, s.value.blue, null)
        if (s.cachedHue < 0f) s.cachedHue = hsb[0]
        val hue = s.cachedHue
        val alpha = s.value.alpha

        // Saturation-Brightness gradient
        val sbH = COLOR_PICKER_H - HUE_BAR_H - ALPHA_BAR_H - 38
        val sbTop = pickerY + 3
        val cols = areaW / SB_SIZE
        val rows = sbH / SB_SIZE

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val sat = col.toFloat() / (cols - 1)
                val bri = 1f - row.toFloat() / (rows - 1)
                val c = Color(Color.HSBtoRGB(hue, sat, bri))
                ClickGUITheme.fill(ctx, padX + col * SB_SIZE, sbTop + row * SB_SIZE, SB_SIZE, SB_SIZE, c.rgb)
            }
        }

        ClickGUITheme.drawBorder(ctx, padX - 1, sbTop - 1, areaW + 2, sbH + 2, cBorder)

        // Crosshair indicator
        val cursorCol = (hsb[1] * (cols - 1)).toInt().coerceIn(0, cols - 1)
        val cursorRow = ((1f - hsb[2]) * (rows - 1)).toInt().coerceIn(0, rows - 1)
        val crX = padX + cursorCol * SB_SIZE
        val crY = sbTop + cursorRow * SB_SIZE
        ClickGUITheme.fill(ctx, crX - 2, crY, 5, 1, cCrosshairDark)
        ClickGUITheme.fill(ctx, crX, crY - 2, 1, 5, cCrosshairDark)
        ClickGUITheme.fill(ctx, crX - 1, crY, 3, 1, cCrosshairLight)
        ClickGUITheme.fill(ctx, crX, crY - 1, 1, 3, cCrosshairLight)

        // Hue bar
        val hueTop = sbTop + sbH + 4
        for (i in 0 until areaW) {
            val h = i.toFloat() / areaW * 0.999f
            val c = Color(Color.HSBtoRGB(h, 1f, 1f))
            ClickGUITheme.fill(ctx, padX + i, hueTop, 1, HUE_BAR_H, c.rgb)
        }
        ClickGUITheme.drawBorder(ctx, padX - 1, hueTop - 1, areaW + 2, HUE_BAR_H + 2, cBorder)

        val hueX = padX + (hue * (areaW - 2)).toInt()
        ClickGUITheme.fill(ctx, hueX - 1, hueTop - 2, 3, HUE_BAR_H + 4, cHueIndicator)

        // Alpha bar
        val alphaTop = hueTop + HUE_BAR_H + 4
        val baseColor = Color(Color.HSBtoRGB(hue, hsb[1], hsb[2]))
        for (i in 0 until areaW) {
            val a = (i.toFloat() / areaW * 255).toInt()
            val c = Color(baseColor.red, baseColor.green, baseColor.blue, a)
            ClickGUITheme.fill(ctx, padX + i, alphaTop, 1, ALPHA_BAR_H, c.rgb)
        }
        ClickGUITheme.drawBorder(ctx, padX - 1, alphaTop - 1, areaW + 2, ALPHA_BAR_H + 2, cBorder)

        // Alpha label
        val alphaLabel = "A"
        ClickGUITheme.drawTextSmall(ctx, padX - ClickGUITheme.textWSmall(alphaLabel) - 2, alphaTop + (ALPHA_BAR_H - fh) / 2, alphaLabel, cTextDark, false)

        val alphaX = padX + (alpha / 255f * (areaW - 2)).toInt()
        ClickGUITheme.fill(ctx, alphaX - 1, alphaTop - 2, 3, ALPHA_BAR_H + 4, cHueIndicator)

        // Hex input row
        val hexTop = alphaTop + ALPHA_BAR_H + 5
        val hexH = 14
        val hexW = areaW
        ClickGUITheme.fill(ctx, padX, hexTop, hexW, hexH, cHexBoxBg)
        ClickGUITheme.drawBorder(ctx, padX, hexTop, hexW, hexH, if (gui.hexEditSetting == s) cAccent else cBorder)

        val isEditingHex = gui.hexEditSetting == s
        val c = s.value
        val hexStr = if (isEditingHex) "#${gui.hexInput}" else String.format("#%02X%02X%02X%02X", c.red, c.green, c.blue, c.alpha)
        val hexCol = if (isEditingHex) cTextBright else cText
        val hexTW = ClickGUITheme.textWSmall(hexStr)
        ClickGUITheme.drawTextSmall(ctx, padX + (hexW - hexTW) / 2, hexTop + (hexH - fh) / 2, hexStr, hexCol)

        if (isEditingHex && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val curX = padX + (hexW - hexTW) / 2 + ClickGUITheme.textWSmall("#${gui.hexInput}")
            ClickGUITheme.fill(ctx, curX, hexTop + 3, 1, hexH - 6, cAccent)
        }
    }

    private fun drawActionSetting(ctx: DrawContext, px: Int, y: Int, s: ActionSetting, mx: Int, my: Int, clipTop: Int, clipBot: Int) {
        val hovered = mx in px..(px + PW) && my in y.coerceAtLeast(clipTop)..(y + SH + y).coerceAtMost(clipBot)
        val nameW = ClickGUITheme.textWSmall(s.name)
        val fh = (tr.fontHeight * SETTING_SCALE).toInt()
        val textX = px + (PW - nameW) / 2
        val textY = y + (SH - fh) / 2

        ClickGUITheme.drawTextSmall(ctx, textX, textY, s.name, if (hovered) cTextBright else cAccent)

        if (hovered) {
            ClickGUITheme.fill(ctx, textX, textY + fh, nameW, 1, cAccent)
        }
    }
}
