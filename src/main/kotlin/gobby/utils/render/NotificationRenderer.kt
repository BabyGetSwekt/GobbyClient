package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render2DEvent
import gobby.gui.click.ClickGUITheme
import gobby.utils.timer.Clock
import net.minecraft.client.gui.DrawContext
import java.awt.Color

object NotificationRenderer {

    private const val MAX_NOTIFICATIONS = 10
    private const val DURATION_MS = 3000L
    private const val SLIDE_MS = 200L
    private const val WIDTH = 140
    private const val HEIGHT = 24
    private const val PADDING = 4
    private const val BAR_HEIGHT = 2

    private val notifications = mutableListOf<Notification>()

    private data class Notification(
        val name: String,
        val enabled: Boolean,
        val clock: Clock = Clock()
    )

    fun show(name: String, enabled: Boolean) {
        notifications.add(0, Notification(name, enabled))
        if (notifications.size > MAX_NOTIFICATIONS) {
            notifications.removeAt(notifications.size - 1)
        }
    }

    @SubscribeEvent
    fun onRender2D(event: Render2DEvent) {
        if (notifications.isEmpty()) return
        notifications.removeAll { it.clock.getTime() > DURATION_MS + SLIDE_MS }
        if (notifications.isEmpty()) return

        val ctx = event.matrices
        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight

        for ((index, notif) in notifications.withIndex()) {
            val elapsed = notif.clock.getTime()
            val slideIn = (elapsed.toFloat() / SLIDE_MS).coerceIn(0f, 1f)
            val slideOut = if (elapsed > DURATION_MS) ((elapsed - DURATION_MS).toFloat() / SLIDE_MS).coerceIn(0f, 1f) else 0f
            val slideOffset = ((1f - slideIn) * (WIDTH + 10)).toInt() + (slideOut * (WIDTH + 10)).toInt()
            val alpha = if (slideOut > 0f) 1f - slideOut else slideIn

            val x = screenWidth - WIDTH - 6 + slideOffset
            val y = screenHeight - 40 - (index * (HEIGHT + PADDING))

            drawNotification(ctx, notif, x, y, elapsed, alpha)
        }
    }

    private fun drawNotification(ctx: DrawContext, notif: Notification, x: Int, y: Int, elapsed: Long, alpha: Float) {
        val tr = mc.textRenderer
        val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)

        ctx.fill(x, y, x + WIDTH, y + HEIGHT, Color(20, 20, 25, (alpha * 180).toInt().coerceIn(0, 255)).rgb)

        val progress = 1f - (elapsed.toFloat() / DURATION_MS).coerceIn(0f, 1f)
        val barColor = if (notif.enabled) Color(80, 220, 100, alphaInt) else Color(220, 60, 60, alphaInt)
        ctx.fill(x, y, x + (WIDTH * progress).toInt(), y + BAR_HEIGHT, barColor.rgb)

        val nameText = ClickGUITheme.styledText(notif.name)
        val stateText = ClickGUITheme.styledText(if (notif.enabled) "ON" else "OFF")
        val nameWidth = tr.getWidth(nameText)
        val stateWidth = tr.getWidth(stateText)
        val scale = if (nameWidth + 4 + stateWidth > WIDTH - 8) (WIDTH - 8f) / (nameWidth + 4 + stateWidth) else 1f
        val textY = y + BAR_HEIGHT + (HEIGHT - BAR_HEIGHT - (tr.fontHeight * scale).toInt()) / 2

        ctx.matrices.pushMatrix()
        ctx.matrices.translate(x + 4f, textY.toFloat())
        ctx.matrices.scale(scale, scale)
        ctx.drawText(tr, nameText, 0, 0, Color(220, 220, 225, alphaInt).rgb, false)
        ctx.drawText(tr, stateText, nameWidth + 4, 0, barColor.rgb, false)
        ctx.matrices.popMatrix()
    }
}
