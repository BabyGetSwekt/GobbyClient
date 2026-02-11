package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render2DEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.render.Render2D.drawString
import java.awt.Color

object Renderer {

    private var displayTitle = ""
    private var titleTicks = 0
    private var displayColor = Color(0, 0, 0)
    private var scale = 4f // changed from 2f



    fun displayTitle(title: String, ticks: Int, color: Color = Color.WHITE) {
        displayTitle = title
        titleTicks = ticks
        displayColor = color
    }

    private fun clearTitle() {
        displayTitle = ""
        titleTicks = 0
    }

    @SubscribeEvent
    fun onRender2D(event: Render2DEvent) {
        if (titleTicks > 0 && displayTitle.isNotEmpty()) {
            val screenWidth = mc.window.scaledWidth
            val screenHeight = mc.window.scaledHeight
            val textWidth = mc.textRenderer.getWidth(displayTitle) * scale
            val x = (screenWidth / 2f) - (textWidth / 2f)
            val y = (screenHeight / 2f) - 30f
            modMessage("onRender2D called with title: $displayTitle and ticks: $titleTicks, scale $scale")

            drawString(
                displayTitle,
                x,
                y,
                displayColor,
                scale,
                event.matrices
            )
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent) {
        if (titleTicks > 0) {
            titleTicks--
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        clearTitle()
    }
}