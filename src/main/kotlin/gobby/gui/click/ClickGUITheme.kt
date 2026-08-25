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

const val SCROLL_EASE = 0.35f
const val SCROLL_SNAP = 0.5f
const val PANEL_W = 520
const val PANEL_H = 360
const val SIDEBAR_ITEM_H = 26


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
const val SETTING_INDENT = 10
const val COLOR_PICKER_H = 114
const val HUE_BAR_H = 10
const val ALPHA_BAR_H = 10
const val SB_SIZE = 2
const val SETTING_SCALE = 0.75f




val cBorder      = Color(50, 50, 58, 140).rgb

val tr get() = mc.font

fun styledText(s: String): Component = Component.literal(s).setStyle(FONT_STYLE)


fun textWScaled(s: String, scale: Float): Int = (tr.width(styledText(s)) * scale).toInt()

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
