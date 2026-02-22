package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.gui.GuiElement
import gobby.gui.GuiElementManager
import gobby.utils.render.Render2D.drawString
import net.minecraft.client.gui.DrawContext
import java.awt.Color

object TitleUtils : GuiElement() {

    private var title = ""
    private var color = Color.WHITE
    private var scale = 4f

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
        show(durationMs = ms, fadeInMs = fadeIn, fadeOutMs = fadeOut)
    }

    override fun hide() {
        super.hide()
        title = ""
    }

    override fun render(drawContext: DrawContext, screenWidth: Int, screenHeight: Int, alpha: Float) {
        if (title.isEmpty()) return

        val textWidth = mc.textRenderer.getWidth(title) * scale
        val x = (screenWidth / 2f) - (textWidth / 2f)
        val y = (screenHeight / 2f) - 30f

        val renderColor = Color(color.red, color.green, color.blue, (alpha * 255).toInt().coerceIn(0, 255))
        drawString(title, x, y, renderColor, scale, drawContext)
    }
}
