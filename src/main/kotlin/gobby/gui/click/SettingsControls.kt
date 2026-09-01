package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

private const val LABEL_PAD = 9
private const val RIGHT_PAD = 10
private const val CHEVRON_W = 7
private const val HALF_TURN = Math.PI.toFloat()
private const val BOX_RADIUS = 3
private const val TRACK_W = 46
private const val TRACK_GAP = 6
private const val INPUT_H = 13
private const val INPUT_PAD = 4
private const val KEY_PAD = 4
private const val SWATCH = 11
private const val ACTION_RADIUS = 4
private const val ROW_HOVER_RADIUS = 4
private const val SWATCH_RADIUS = 3
private const val SWATCH_EDGE = 1
private const val REFRESH_BUSY_LABEL = "Refreshing..."

internal object SettingsControls {

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, row: PlacedRow, hovered: Boolean, mx: Int, my: Int) {
        if (hovered) GobbyDraw.roundedRect(ctx, row.x, row.y, row.w, row.h, ROW_HOVER_RADIUS, cRowHover)
        when (val s = row.setting) {
            is ModelPreviewSetting -> SettingsPreview.draw(ctx, gui, s, Rect(row.x, row.y, row.w, row.h), mx, my)
            is BooleanSetting -> booleanRow(ctx, row, s)
            is NumberSetting -> numberRow(ctx, gui, row, s)
            is RangeSetting -> rangeRow(ctx, row, s)
            is SelectorSetting -> selectorRow(ctx, gui, row, s)
            is ColorSetting -> colorRow(ctx, row, s)
            is KeybindSetting -> keybindRow(ctx, gui, row, s)
            is StringSetting -> stringRow(ctx, gui, row, s)
            is ActionSetting -> buttonRow(ctx, row, s.name, hovered)
            is TextSetting -> textRow(ctx, row, s)
            is RefreshSetting -> refreshRow(ctx, row, s, hovered)
            is HudButton -> hudRow(ctx, row, s, hovered)
            is DropDownSetting -> dropdownRow(ctx, gui, row, s)
        }
    }

    private fun refreshRow(ctx: GuiGraphicsExtractor, row: PlacedRow, s: RefreshSetting, hovered: Boolean) {
        val busy = s.busy()
        buttonRow(ctx, row, if (busy) REFRESH_BUSY_LABEL else s.name, hovered && !busy)
    }

    private fun textRow(ctx: GuiGraphicsExtractor, row: PlacedRow, s: TextSetting) {
        label(ctx, row, s.name)
        val text = s.text()
        val w = textWScaled(text, SETTINGS_VALUE_SCALE)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, row.x + row.w - RIGHT_PAD - w, row.y + (row.h - h) / 2, text, SETTINGS_VALUE_SCALE, cInkSoft, false)
    }

    private fun label(ctx: GuiGraphicsExtractor, row: PlacedRow, text: String) {
        val h = (tr.lineHeight * SETTINGS_LABEL_SCALE).toInt()
        drawTextScaled(ctx, row.x + LABEL_PAD, row.y + (row.h - h) / 2, text, SETTINGS_LABEL_SCALE, cInk, false)
    }

    private fun labelTop(ctx: GuiGraphicsExtractor, row: PlacedRow, text: String) {
        drawTextScaled(ctx, row.x + LABEL_PAD, row.y + 5, text, SETTINGS_LABEL_SCALE, cInk, false)
    }

    fun pillRect(row: PlacedRow) = Rect(row.x + row.w - PILL_W - RIGHT_PAD, row.y + (row.h - PILL_H) / 2, PILL_W, PILL_H)

    private fun booleanRow(ctx: GuiGraphicsExtractor, row: PlacedRow, s: BooleanSetting) {
        label(ctx, row, s.name)
        pill(ctx, pillRect(row), s.value)
    }

    fun pill(ctx: GuiGraphicsExtractor, r: Rect, on: Boolean) {
        GobbyTextures.capsule(ctx, r.x, r.y, r.w, r.h, if (on) cViolet else cPillOff)
        val knobX = if (on) r.x + r.w - PILL_KNOB - PILL_INSET else r.x + PILL_INSET
        GobbyTextures.disc(ctx, knobX, r.y + (r.h - PILL_KNOB) / 2, PILL_KNOB, if (on) cPillKnob else cPillKnobOff)
    }

    private fun sliderKnob(ctx: GuiGraphicsExtractor, centerX: Int, centerY: Int) =
        GobbyTextures.disc(ctx, centerX - TRACK_KNOB / 2, centerY - TRACK_KNOB / 2, TRACK_KNOB, cPillKnob)

    fun trackRect(row: PlacedRow) = Rect(
        row.x + row.w - RIGHT_PAD - VALUE_BOX_W - TRACK_GAP - TRACK_W,
        row.y + (row.h - TRACK_H) / 2,
        TRACK_W, TRACK_H
    )

    private fun numberRow(ctx: GuiGraphicsExtractor, gui: ClickGUI, row: PlacedRow, s: NumberSetting) {
        label(ctx, row, s.name)
        val t = trackRect(row)
        GobbyTextures.capsule(ctx, t.x, t.y, t.w, t.h, cTrack)
        val filled = (t.w * s.progress).roundToInt()
        if (filled > 0) GobbyTextures.capsule(ctx, t.x, t.y, filled, t.h, cViolet)
        sliderKnob(ctx, t.x + filled, t.y + t.h / 2)

        if (gui.numberEditSetting == s) editableValueBox(ctx, row, gui.numberField)
        else valueBox(ctx, row, s.display())
    }

    private fun editableValueBox(ctx: GuiGraphicsExtractor, row: PlacedRow, field: TextField) {
        val b = valueBoxRect(row)
        val invalid = !NumberInput.isValid(field.text)
        GobbyDraw.roundedBox(ctx, b.x, b.y, b.w, b.h, BOX_RADIUS, cValueBox, if (invalid) cInvalid else cViolet)
        val x = b.x + (b.w - textWScaled(field.text, SETTINGS_VALUE_SCALE)) / 2
        TextFieldView.draw(ctx, field, x, b.y, b.h, SETTINGS_VALUE_SCALE, if (invalid) cInvalid else cInk, true)
    }

    fun valueBoxRect(row: PlacedRow) = Rect(
        row.x + row.w - RIGHT_PAD - VALUE_BOX_W,
        row.y + (row.h - VALUE_BOX_H) / 2,
        VALUE_BOX_W, VALUE_BOX_H
    )

    private fun valueBox(ctx: GuiGraphicsExtractor, row: PlacedRow, text: String) {
        val b = valueBoxRect(row)
        GobbyDraw.roundedBox(ctx, b.x, b.y, b.w, b.h, BOX_RADIUS, cValueBox, cValueBox)
        val w = textWScaled(text, SETTINGS_VALUE_SCALE)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, b.x + (b.w - w) / 2, b.y + (b.h - h) / 2, text, SETTINGS_VALUE_SCALE, cInk, false)
    }

    private fun rangeRow(ctx: GuiGraphicsExtractor, row: PlacedRow, s: RangeSetting) {
        label(ctx, row, s.name)
        val t = trackRect(row)
        GobbyTextures.capsule(ctx, t.x, t.y, t.w, t.h, cTrack)
        val lowX = t.x + (t.w * s.progress(s.value.start)).roundToInt()
        val highX = t.x + (t.w * s.progress(s.value.endInclusive)).roundToInt()
        GobbyTextures.capsule(ctx, lowX, t.y, (highX - lowX).coerceAtLeast(1), t.h, cViolet)
        sliderKnob(ctx, lowX, t.y + t.h / 2)
        sliderKnob(ctx, highX, t.y + t.h / 2)
        valueBox(ctx, row, "${trim(s.value.start)}-${trim(s.value.endInclusive)}")
    }

    private fun trim(v: Float): String = if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()

    private fun dropdownRow(ctx: GuiGraphicsExtractor, gui: ClickGUI, row: PlacedRow, s: DropDownSetting) {
        label(ctx, row, s.name)
        chevron(ctx, row.x + row.w - RIGHT_PAD - CHEVRON_W, row.y + row.h / 2, gui.flip(s, s.expanded))
    }

    private fun selectorRow(ctx: GuiGraphicsExtractor, gui: ClickGUI, row: PlacedRow, s: SelectorSetting) {
        label(ctx, row, s.name)
        val text = s.options.getOrElse(s.value) { "?" }
        val w = textWScaled(text, SETTINGS_VALUE_SCALE)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        val textX = row.x + row.w - RIGHT_PAD - CHEVRON_W - 5 - w
        drawTextScaled(ctx, textX, row.y + (row.h - h) / 2, text, SETTINGS_VALUE_SCALE, cInkSoft, false)
        chevron(ctx, row.x + row.w - RIGHT_PAD - CHEVRON_W, row.y + row.h / 2, gui.flip(s, gui.openSelector === s))
    }

    private fun chevron(ctx: GuiGraphicsExtractor, x: Int, centerY: Int, flip: Float) =
        GobbyTextures.triangle(ctx, x, centerY - CHEVRON_W / 2, CHEVRON_W, cInkFaint, HALF_TURN * flip)

    fun swatchRect(row: PlacedRow) = Rect(row.x + row.w - SWATCH - RIGHT_PAD, row.y + (row.h - SWATCH) / 2, SWATCH, SWATCH)

    private fun colorRow(ctx: GuiGraphicsExtractor, row: PlacedRow, s: ColorSetting) {
        label(ctx, row, s.name)
        val r = swatchRect(row)
        GobbyDraw.roundedRect(
            ctx, r.x - SWATCH_EDGE, r.y - SWATCH_EDGE, r.w + SWATCH_EDGE * 2, r.h + SWATCH_EDGE * 2,
            ACTION_RADIUS, cCardEdge
        )
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, SWATCH_RADIUS, s.value.rgb or OPAQUE_BITS)
    }

    fun keyBoxRect(row: PlacedRow, text: String): Rect {
        val w = textWScaled(text, SETTINGS_VALUE_SCALE) + KEY_PAD * 2
        return Rect(row.x + row.w - RIGHT_PAD - w, row.y + (row.h - VALUE_BOX_H) / 2, w, VALUE_BOX_H)
    }

    private fun keybindRow(ctx: GuiGraphicsExtractor, gui: ClickGUI, row: PlacedRow, s: KeybindSetting) {
        label(ctx, row, s.name)
        val listening = gui.listeningKeybind == s
        val text = if (listening) "..." else s.getKeyName()
        val b = keyBoxRect(row, text)
        val keyFill = if (listening) cVioletSoft else cValueBox
        GobbyDraw.roundedBox(ctx, b.x, b.y, b.w, b.h, BOX_RADIUS, keyFill, if (listening) cViolet else keyFill)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, b.x + KEY_PAD, b.y + (b.h - h) / 2, text, SETTINGS_VALUE_SCALE, cInkSoft, false)
    }

    fun inputRect(row: PlacedRow) = Rect(row.x + LABEL_PAD, row.y + row.h - INPUT_H - 4, row.w - LABEL_PAD * 2, INPUT_H)

    fun stringTextOrigin(row: PlacedRow): Int = inputRect(row).x + INPUT_PAD

    fun numberTextOrigin(row: PlacedRow, text: String): Int {
        val b = valueBoxRect(row)
        return b.x + (b.w - textWScaled(text, SETTINGS_VALUE_SCALE)) / 2
    }

    private fun stringRow(ctx: GuiGraphicsExtractor, gui: ClickGUI, row: PlacedRow, s: StringSetting) {
        labelTop(ctx, row, s.name)
        val b = inputRect(row)
        val editing = gui.stringEditSetting == s
        GobbyDraw.roundedBox(ctx, b.x, b.y, b.w, b.h, BOX_RADIUS, cValueBox, if (editing) cViolet else cValueBox)
        if (editing) {
            TextFieldView.draw(ctx, gui.stringField, b.x + INPUT_PAD, b.y, b.h, SETTINGS_VALUE_SCALE, cInk, true)
        } else {
            val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
            drawTextScaled(ctx, b.x + INPUT_PAD, b.y + (b.h - h) / 2, s.value, SETTINGS_VALUE_SCALE, cInk, false)
        }
    }

    private fun buttonRow(ctx: GuiGraphicsExtractor, row: PlacedRow, text: String, highlight: Boolean) {
        val b = Rect(row.x + 4, row.y + 3, row.w - 8, row.h - 6)
        GobbyDraw.roundedRect(ctx, b.x, b.y, b.w, b.h, ACTION_RADIUS, if (highlight) cViolet else cValueBox)
        val w = textWScaled(text, SETTINGS_LABEL_SCALE)
        val h = (tr.lineHeight * SETTINGS_LABEL_SCALE).toInt()
        drawTextScaled(ctx, b.x + (b.w - w) / 2, b.y + (b.h - h) / 2, text, SETTINGS_LABEL_SCALE, cInk, false)
    }

    private fun hudRow(ctx: GuiGraphicsExtractor, row: PlacedRow, s: HudButton, hovered: Boolean) {
        label(ctx, row, s.name)
        val text = "Edit"
        val w = textWScaled(text, SETTINGS_VALUE_SCALE) + KEY_PAD * 2
        val b = Rect(row.x + row.w - RIGHT_PAD - w, row.y + (row.h - VALUE_BOX_H) / 2, w, VALUE_BOX_H)
        GobbyDraw.roundedRect(ctx, b.x, b.y, b.w, b.h, 3, if (hovered) cViolet else cValueBox)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, b.x + KEY_PAD, b.y + (b.h - h) / 2, text, SETTINGS_VALUE_SCALE, cInkSoft, false)
    }
}
