package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.gui.GuiElement
import gobby.gui.GuiElementManager
import gobby.gui.click.ClickGUITheme
import net.minecraft.client.gui.DrawContext
import java.awt.Color

object TitleUtils : GuiElement() {

    private var title = ""
    private var color = Color.WHITE
    private var scale = 4f
    private var useCustomFont = false

    init {
        GuiElementManager.register(this)
    }

    fun displayTitleTicks(title: String, ticks: Int, color: Color = Color.WHITE, scale: Float = 4f, fadeIn: Int = 0, fadeOut: Int = 0) {
        displayTitleMs(title, ticks * 50L, color, scale, fadeIn * 50L, fadeOut * 50L)
    }

    fun displayTitleMs(title: String, ms: Long, color: Color = Color.WHITE, scale: Float = 4f, fadeIn: Long = 0L, fadeOut: Long = 0L) {
        TitleUtils.title = title
        TitleUtils.color = color
        TitleUtils.scale = scale
        TitleUtils.useCustomFont = false
        show(durationMs = ms, fadeInMs = fadeIn, fadeOutMs = fadeOut)
    }

    fun displayStyledTitleTicks(title: String, ticks: Int, color: Color = Color.WHITE, scale: Float = 4f, fadeIn: Int = 0, fadeOut: Int = 0) {
        TitleUtils.title = title
        TitleUtils.color = color
        TitleUtils.scale = scale
        TitleUtils.useCustomFont = true
        show(durationMs = ticks * 50L, fadeInMs = fadeIn * 50L, fadeOutMs = fadeOut * 50L)
    }

    override fun hide() {
        super.hide()
        title = ""
    }

    override fun render(drawContext: DrawContext, screenWidth: Int, screenHeight: Int, alpha: Float) {
        if (title.isEmpty()) return

        val renderColor = Color(color.red, color.green, color.blue, (alpha * 255).toInt().coerceIn(0, 255))
        val argb = renderColor.rgb

        if (useCustomFont) {
            val tr = mc.textRenderer
            val styledText = ClickGUITheme.styledText(title)
            val textWidth = tr.getWidth(styledText) * scale
            val x = (screenWidth / 2f) - (textWidth / 2f)
            val y = (screenHeight / 2f) - 30f
            drawContext.matrices.pushMatrix()
            drawContext.matrices.translate(x, y)
            drawContext.matrices.scale(scale, scale)
            drawContext.drawText(tr, styledText, 0, 0, argb, true)
            drawContext.matrices.popMatrix()
        } else {
            val textWidth = mc.textRenderer.getWidth(title) * scale
            val x = (screenWidth / 2f) - (textWidth / 2f)
            val y = (screenHeight / 2f) - 30f
            Render2D.drawString(title, x, y, renderColor, scale, drawContext)
        }
    }
}
