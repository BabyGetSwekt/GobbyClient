package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor
import java.awt.Color

private const val POPUP_W = 118
private const val POPUP_PAD = 7
private const val SV_H = 84
private const val BAR_H = 9
private const val BAR_GAP = 6
private const val HEX_H = 16
private const val RADIUS = 6
private const val SV_STEP = 2
private const val CURSOR_R = 3
private const val MAX_HUE = 0.9999f
private const val FULL_ALPHA = 255
private const val HEX_SWATCH = 9
private const val HEX_RADIUS = 4
private const val HEX_PAD = 5
private const val SWATCH_RADIUS = 2
private const val HASH = "#"

internal object ColorPickerPopup {

    private val svTop get() = POPUP_PAD
    private val hueTop get() = svTop + SV_H + BAR_GAP
    private val alphaTop get() = hueTop + BAR_H + BAR_GAP
    private val hexTop get() = alphaTop + BAR_H + BAR_GAP
    private val popupH get() = hexTop + HEX_H + POPUP_PAD

    fun bounds(gui: ClickGUI, row: PlacedRow): Rect {
        val x = (row.x + row.w - POPUP_W).coerceAtLeast(gui.panelX + SIDEBAR_W_SETTINGS + 2)
        val below = row.y + row.h + 3
        val y = if (below + popupH <= gui.panelY + PANEL_H) below else (row.y - popupH - 3)
        return Rect(x, y.coerceAtLeast(gui.panelY + SETTINGS_HEADER_H + 2), POPUP_W, popupH)
    }

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, row: PlacedRow) {
        val s = row.setting as? ColorSetting ?: return
        val r = bounds(gui, row)
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, RADIUS, cShellBg, cShellEdge)

        val hsb = Color.RGBtoHSB(s.value.red, s.value.green, s.value.blue, null)
        if (s.cachedHue < 0f) s.cachedHue = hsb[0]
        val areaX = r.x + POPUP_PAD
        val areaW = r.w - POPUP_PAD * 2

        drawSaturation(ctx, areaX, r.y + svTop, areaW, s.cachedHue, hsb)
        drawHue(ctx, areaX, r.y + hueTop, areaW, s.cachedHue)
        drawAlpha(ctx, areaX, r.y + alphaTop, areaW, s, hsb)
        drawHex(ctx, gui, areaX, r.y + hexTop, areaW, s)
    }

    private fun drawSaturation(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, hue: Float, hsb: FloatArray) {
        val cols = w / SV_STEP
        val rows = SV_H / SV_STEP
        (0 until rows).forEach { row ->
            val bri = 1f - row.toFloat() / (rows - 1).coerceAtLeast(1)
            (0 until cols).forEach { col ->
                val sat = col.toFloat() / (cols - 1).coerceAtLeast(1)
                ctx.fill(
                    x + col * SV_STEP, y + row * SV_STEP,
                    x + col * SV_STEP + SV_STEP, y + row * SV_STEP + SV_STEP,
                    Color(Color.HSBtoRGB(hue, sat, bri)).rgb
                )
            }
        }
        val cx = x + (hsb[1] * w).toInt()
        val cy = y + ((1f - hsb[2]) * SV_H).toInt()
        GobbyDraw.roundedOutline(ctx, cx - CURSOR_R, cy - CURSOR_R, CURSOR_R * 2, CURSOR_R * 2, CURSOR_R, cPillKnob)
    }

    private fun drawHue(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, hue: Float) {
        (0 until w).forEach { i ->
            ctx.fill(x + i, y, x + i + 1, y + BAR_H, Color(Color.HSBtoRGB(i.toFloat() / w * MAX_HUE, 1f, 1f)).rgb)
        }
        knob(ctx, x + (hue * w).toInt(), y)
    }

    private fun drawAlpha(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, s: ColorSetting, hsb: FloatArray) {
        val base = Color(Color.HSBtoRGB(s.cachedHue, hsb[1], hsb[2]))
        (0 until w).forEach { i ->
            val a = (i.toFloat() / w * FULL_ALPHA).toInt()
            ctx.fill(x + i, y, x + i + 1, y + BAR_H, Color(base.red, base.green, base.blue, a).rgb)
        }
        knob(ctx, x + (s.value.alpha / FULL_ALPHA.toFloat() * w).toInt(), y)
    }

    private fun knob(ctx: GuiGraphicsExtractor, x: Int, y: Int) =
        GobbyDraw.roundedOutline(ctx, x - 2, y - 1, 5, BAR_H + 2, 2, cPillKnob)

    private fun textOrigin(x: Int) = x + HEX_PAD + textWScaled("Hex", SETTINGS_VALUE_SCALE) + HEX_PAD + HEX_SWATCH + HEX_PAD

    fun caretIndexAt(gui: ClickGUI, row: PlacedRow, mouseX: Int): Int {
        val r = bounds(gui, row)
        val origin = textOrigin(r.x + POPUP_PAD) + textWScaled(HASH, SETTINGS_VALUE_SCALE)
        return TextFieldView.caretIndexAt(gui.hexField.text, origin, mouseX, SETTINGS_VALUE_SCALE)
    }

    private fun drawHex(ctx: GuiGraphicsExtractor, gui: ClickGUI, x: Int, y: Int, w: Int, s: ColorSetting) {
        val editing = gui.hexEditSetting == s
        val field = gui.hexField
        val shown = if (editing) field.text else HexColor.format(s.value)
        val invalid = editing && shown.isNotEmpty() && !HexColor.isComplete(shown)

        val hexBorder = if (!editing) cValueBox else if (invalid) cInvalid else cViolet
        GobbyDraw.roundedBox(ctx, x, y, w, HEX_H, HEX_RADIUS, cValueBox, hexBorder)

        val labelH = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        val textY = y + (HEX_H - labelH) / 2
        drawTextScaled(ctx, x + HEX_PAD, textY, "Hex", SETTINGS_VALUE_SCALE, cInkGhost, false)
        GobbyDraw.roundedRect(ctx, x + HEX_PAD + textWScaled("Hex", SETTINGS_VALUE_SCALE) + HEX_PAD, y + (HEX_H - HEX_SWATCH) / 2, HEX_SWATCH, HEX_SWATCH, SWATCH_RADIUS, s.value.rgb or OPAQUE_BITS)

        val origin = textOrigin(x)
        drawTextScaled(ctx, origin, textY, HASH, SETTINGS_VALUE_SCALE, cInkGhost, false)
        val bodyX = origin + textWScaled(HASH, SETTINGS_VALUE_SCALE)

        TextFieldView.draw(ctx, field, bodyX, y, HEX_H, SETTINGS_VALUE_SCALE, if (invalid) cInvalid else cInk, editing, shown)
    }

    fun handleClick(gui: ClickGUI, row: PlacedRow, mx: Int, my: Int): Boolean {
        val s = row.setting as? ColorSetting ?: return false
        val r = bounds(gui, row)
        if ((mx to my) !in r) {
            s.expanded = false
            gui.hexEditSetting = null
            return false
        }

        gui.colorPickerBaseX = r.x + POPUP_PAD
        gui.colorPickerBaseW = r.w - POPUP_PAD * 2
        gui.colorPickerSBTop = r.y + svTop
        gui.colorPickerSBH = SV_H

        when (my) {
            in (r.y + svTop) until (r.y + svTop + SV_H) -> gui.draggingColorSB = s
            in (r.y + hueTop) until (r.y + hueTop + BAR_H) -> gui.draggingColorHue = s
            in (r.y + alphaTop) until (r.y + alphaTop + BAR_H) -> gui.draggingColorAlpha = s
            in (r.y + hexTop) until (r.y + hexTop + HEX_H) -> {
                if (gui.hexEditSetting !== s) {
                    gui.hexEditSetting = s
                    gui.hexField.reset(HexColor.format(s.value))
                }
                gui.hexField.placeCaret(caretIndexAt(gui, row, mx), extend = false)
                gui.draggingHex = true
                return true
            }
        }
        ColorPickerInput.handleDrag(gui, mx.toDouble(), my.toDouble())
        return true
    }
}
