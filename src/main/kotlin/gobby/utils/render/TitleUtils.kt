package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render2DEvent
import gobby.utils.render.Render2D.drawString
import gobby.utils.timer.Clock
import java.awt.Color

object TitleUtils {

    private var title = ""
    private var color = Color.WHITE
    private var scale = 4f
    private var fadeIn = 0L
    private var fadeOut = 0L
    private var duration = 0L
    private val clock = Clock()
    private var active = false

    fun displayTitleTicks(title: String, ticks: Int, color: Color = Color.WHITE, scale: Float = 4f, fadeIn: Int = 0, fadeOut: Int = 0) {
        displayTitleMs(title, ticks * 50L, color, scale, fadeIn * 50L, fadeOut * 50L)
    }

    fun displayTitleMs(title: String, ms: Long, color: Color = Color.WHITE, scale: Float = 4f, fadeIn: Long = 0L, fadeOut: Long = 0L) {
        TitleUtils.title = title
        TitleUtils.color = color
        TitleUtils.scale = scale
        TitleUtils.fadeIn = fadeIn
        TitleUtils.fadeOut = fadeOut
        duration = ms
        clock.update()
        active = true
    }

    private fun clear() {
        active = false
        title = ""
    }

    private fun getAlpha(): Float {
        val elapsed = clock.getTime()
        val totalTime = fadeIn + duration + fadeOut

        if (elapsed >= totalTime) {
            clear()
            return 0f
        }

        if (elapsed < fadeIn && fadeIn > 0) {
            return elapsed.toFloat() / fadeIn
        }

        if (elapsed > fadeIn + duration && fadeOut > 0) {
            val fadeOutElapsed = elapsed - fadeIn - duration
            return 1f - (fadeOutElapsed.toFloat() / fadeOut)
        }

        return 1f
    }

    @SubscribeEvent
    fun onRender2D(event: Render2DEvent) {
        if (!active || title.isEmpty()) return

        val alpha = getAlpha()
        if (alpha <= 0f) return

        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight
        val textWidth = mc.textRenderer.getWidth(title) * scale
        val x = (screenWidth / 2f) - (textWidth / 2f)
        val y = (screenHeight / 2f) - 30f

        val renderColor = Color(color.red, color.green, color.blue, (alpha * 255).toInt().coerceIn(0, 255))

        drawString(title, x, y, renderColor, scale, event.matrices)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        clear()
    }
}
