package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier as ResourceLocation
import java.awt.Color

internal data class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
    operator fun contains(p: Pair<Int, Int>): Boolean = p.first in x..(x + w) && p.second in y..(y + h)
}


val FONT_STYLE: Style = Style.EMPTY.withFont(
    FontDescription.Resource(ResourceLocation.fromNamespaceAndPath("gobbyclient", "custom"))
)

const val PANEL_W = 540
const val PANEL_H = 360
const val SIDEBAR_W_COLLAPSED = 44
const val SIDEBAR_W_EXPANDED = 95
const val HEADER_H = 32
const val CONTENT_PAD = 10
const val SIDEBAR_ITEM_H = 26

const val GRID_COLS = 3
const val CARD_GAP = 8

const val PW = 165
const val SH = 16
const val GAP = 8
const val PAD = 6
const val TOGGLE_W = 20
const val TOGGLE_H = 10
const val KNOB_W = 8
const val KNOB_H = 8
const val SLIDER_H = 3
const val RANGE_THUMB_W = 5
const val RANGE_THUMB_H = 7
const val STRING_TEXT_PAD = 3
const val CURSOR_BLINK_MS = 500L
const val SETTING_INDENT = 10
const val COLOR_PICKER_H = 114
const val HUE_BAR_H = 10
const val ALPHA_BAR_H = 10
const val SB_SIZE = 2
const val SETTING_SCALE = 0.75f

val cPanelBg     = Color(14, 14, 16, 240).rgb
val cSidebarBg   = Color(10, 10, 12, 235).rgb
val cHeaderBg    = Color(16, 16, 19, 245).rgb
val cSettingBg   = Color(22, 22, 26, 230).rgb
val cCardBg      = Color(24, 24, 28, 235).rgb
val cCardBgHov   = Color(32, 32, 38, 240).rgb
val cSearchBg    = Color(18, 18, 22, 240).rgb

val cAccent      = Color(60, 200, 95, 255).rgb
val cAccentDim   = Color(60, 200, 95, 60).rgb
val cAccentGlow  = Color(60, 200, 95, 120).rgb
val cEnabled     = Color(60, 200, 95, 220).rgb

val cHover       = Color(255, 255, 255, 18).rgb
val cTextBright  = Color(240, 240, 240, 255).rgb
val cText        = Color(195, 195, 200, 255).rgb
val cTextGray    = Color(135, 135, 140, 255).rgb
val cTextDark    = Color(85, 85, 92, 255).rgb

val cToggleOn    = Color(60, 200, 95, 255).rgb
val cToggleOff   = Color(38, 38, 44, 255).rgb
val cKnob        = Color(225, 225, 230, 255).rgb
val cSliderTrack = Color(34, 34, 40, 255).rgb
val cSliderFill  = Color(60, 200, 95, 255).rgb
val cBorder      = Color(50, 50, 58, 140).rgb
val cBorderLight = Color(255, 255, 255, 10).rgb
val cScrollbar   = Color(60, 200, 95, 140).rgb
val cKeyBox      = Color(38, 38, 46, 255).rgb
val cKeyBoxBorder = Color(60, 60, 70, 200).rgb
val cSeparator   = Color(0, 0, 0, 60).rgb
val cPickerBg    = Color(14, 14, 18, 245).rgb
val cCrosshairDark = Color(0, 0, 0, 180).rgb
val cCrosshairLight = Color(255, 255, 255, 240).rgb
val cHueIndicator = Color(255, 255, 255, 230).rgb
val cHexBoxBg    = Color(28, 28, 34, 255).rgb
val cTooltipBg   = Color(10, 10, 14, 240).rgb

val tr get() = mc.font

fun styledText(s: String): Component = Component.literal(s).setStyle(FONT_STYLE)


fun textWSmall(s: String): Int = (tr.width(styledText(s)) * SETTING_SCALE).toInt()

fun textWScaled(s: String, scale: Float): Int = (tr.width(styledText(s)) * scale).toInt()

fun drawText(ctx: GuiGraphicsExtractor, x: Int, y: Int, s: String, color: Int, shadow: Boolean = true) {
    ctx.text(tr, styledText(s), x, y, color, shadow)
}

fun drawTextSmall(ctx: GuiGraphicsExtractor, x: Int, y: Int, s: String, color: Int, shadow: Boolean = true) {
    drawTextScaled(ctx, x, y, s, SETTING_SCALE, color, shadow)
}

fun drawTextScaled(ctx: GuiGraphicsExtractor, x: Int, y: Int, s: String, scale: Float, color: Int, shadow: Boolean = true) {
    ctx.pose().pushMatrix()
    ctx.pose().translate(x.toFloat(), y.toFloat())
    ctx.pose().scale(scale, scale)
    ctx.text(tr, styledText(s), 0, 0, color, shadow)
    ctx.pose().popMatrix()
}

fun fill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
    ctx.fill(x, y, x + w, y + h, color)
}

fun drawBorder(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
    fill(ctx, x, y, w, 1, color)
    fill(ctx, x, y + h - 1, w, 1, color)
    fill(ctx, x, y + 1, 1, h - 2, color)
    fill(ctx, x + w - 1, y + 1, 1, h - 2, color)
}

fun roundedFill(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
    fill(ctx, x + 1, y, w - 2, h, color)
    fill(ctx, x, y + 1, 1, h - 2, color)
    fill(ctx, x + w - 1, y + 1, 1, h - 2, color)
}
