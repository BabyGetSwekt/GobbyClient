package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Style
import net.minecraft.text.StyleSpriteSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.awt.Color

object ClickGUITheme {

    // Font
    private val CUSTOM_FONT = StyleSpriteSource.Font(Identifier.of("gobbyclient", "custom"))
    val FONT_STYLE: Style = Style.EMPTY.withFont(CUSTOM_FONT)

    // Layout
    const val PW = 130
    const val HH = 22
    const val MH = 20
    const val SH = 16
    const val GAP = 8
    const val PAD = 6
    const val BOTTOM_ACCENT = 2
    const val TOGGLE_W = 20
    const val TOGGLE_H = 10
    const val KNOB_W = 8
    const val KNOB_H = 8
    const val SLIDER_H = 3
    const val SCROLLBAR_W = 3
    const val SETTING_INDENT = 10
    const val COLOR_PICKER_H = 114
    const val HUE_BAR_H = 10
    const val ALPHA_BAR_H = 10
    const val SB_SIZE = 2
    const val SETTING_SCALE = 0.75f

    // Colors
    val cPanelBg    = Color(28, 28, 34, 235).rgb
    val cHeaderBg   = Color(22, 22, 28, 250).rgb
    val cSettingBg  = Color(24, 24, 30, 230).rgb
    val cSearchBg   = Color(22, 22, 28, 240).rgb

    val cAccent     = Color(0, 180, 200, 255).rgb
    val cEnabled    = Color(50, 180, 80, 200).rgb
    val cEnabledHov = Color(60, 200, 95, 220).rgb
    val cHeaderLine = Color(0, 180, 200, 90).rgb

    val cHover      = Color(255, 255, 255, 16).rgb
    val cTextBright = Color(240, 240, 248, 255).rgb
    val cText       = Color(195, 195, 205, 255).rgb
    val cTextGray   = Color(130, 130, 145, 255).rgb
    val cTextDark   = Color(70, 70, 85, 255).rgb

    val cToggleOn   = Color(0, 180, 200, 255).rgb
    val cToggleOff  = Color(45, 45, 55, 255).rgb
    val cKnob       = Color(220, 220, 230, 255).rgb
    val cSliderTrack = Color(38, 38, 48, 255).rgb
    val cSliderFill = Color(0, 180, 200, 255).rgb
    val cBorder     = Color(55, 55, 68, 120).rgb
    val cScrollbar  = Color(90, 90, 110, 120).rgb
    val cKeyBox     = Color(45, 45, 58, 255).rgb
    val cKeyBoxBorder = Color(70, 70, 88, 200).rgb
    val cSeparator  = Color(0, 0, 0, 30).rgb
    val cPickerBg   = Color(18, 18, 24, 245).rgb
    val cCrosshairDark = Color(0, 0, 0, 180).rgb
    val cCrosshairLight = Color(255, 255, 255, 240).rgb
    val cHueIndicator = Color(255, 255, 255, 230).rgb
    val cHexBoxBg   = Color(32, 32, 40, 255).rgb
    val cTooltipBg  = Color(14, 14, 20, 240).rgb

    // Text renderer shortcut
    val tr get() = mc.textRenderer

    fun styledText(s: String): Text = Text.literal(s).setStyle(FONT_STYLE)

    fun textW(s: String): Int = tr.getWidth(styledText(s))

    fun textWSmall(s: String): Int = (tr.getWidth(styledText(s)) * SETTING_SCALE).toInt()

    fun drawText(ctx: DrawContext, x: Int, y: Int, s: String, color: Int, shadow: Boolean = true) {
        ctx.drawText(tr, styledText(s), x, y, color, shadow)
    }

    fun drawTextSmall(ctx: DrawContext, x: Int, y: Int, s: String, color: Int, shadow: Boolean = true) {
        ctx.matrices.pushMatrix()
        ctx.matrices.translate(x.toFloat(), y.toFloat())
        ctx.matrices.scale(SETTING_SCALE, SETTING_SCALE)
        ctx.drawText(tr, styledText(s), 0, 0, color, shadow)
        ctx.matrices.popMatrix()
    }

    fun fill(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        ctx.fill(x, y, x + w, y + h, color)
    }

    fun drawBorder(ctx: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        fill(ctx, x, y, w, 1, color)
        fill(ctx, x, y + h - 1, w, 1, color)
        fill(ctx, x, y + 1, 1, h - 2, color)
        fill(ctx, x + w - 1, y + 1, 1, h - 2, color)
    }
}
