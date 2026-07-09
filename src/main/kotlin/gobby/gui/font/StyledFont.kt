package gobby.gui.font

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.ConstraintType
import gg.essential.elementa.constraints.resolution.ConstraintVisitor
import gg.essential.elementa.font.FontProvider
import gg.essential.universal.UGraphics
import gg.essential.universal.UMatrixStack
import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.FONT_STYLE
import gobby.gui.click.styledText
import net.minecraft.network.chat.Style
import java.awt.Color

private const val BASE_CHAR_HEIGHT = 7f
private const val BELOW_LINE_HEIGHT = 1f
private const val SHADOW_HEIGHT = 1f

object StyledFontHolder {
    private val style = ThreadLocal<Style?>()

    @JvmStatic
    fun current(): Style? = style.get()

    fun set(value: Style) {
        style.set(value)
    }

    fun clear() {
        style.remove()
    }
}

object StyledFontProvider : FontProvider {
    override var cachedValue: FontProvider = this
    override var recalculate: Boolean = false
    override var constrainTo: UIComponent? = null

    override fun visitImpl(visitor: ConstraintVisitor, type: ConstraintType) {}

    override fun getStringWidth(string: String, pointSize: Float): Float =
        mc.font.width(styledText(string)).toFloat()

    override fun getStringHeight(string: String, pointSize: Float): Float =
        mc.font.lineHeight.toFloat()

    override fun drawString(
        matrixStack: UMatrixStack,
        string: String,
        color: Color,
        x: Float,
        y: Float,
        originalPointSize: Float,
        scale: Float,
        shadow: Boolean,
        shadowColor: Color?
    ) {
        val scaledX = x / scale
        val scaledY = y / scale
        matrixStack.scale(scale, scale, 1f)
        StyledFontHolder.set(FONT_STYLE)
        try {
            UGraphics.drawString(matrixStack, string, scaledX, scaledY, color.rgb, shadow)
        } finally {
            StyledFontHolder.clear()
        }
        matrixStack.scale(1f / scale, 1f / scale, 1f)
    }

    override fun getBaseLineHeight(): Float = BASE_CHAR_HEIGHT

    override fun getShadowHeight(): Float = SHADOW_HEIGHT

    override fun getBelowLineHeight(): Float = BELOW_LINE_HEIGHT
}
