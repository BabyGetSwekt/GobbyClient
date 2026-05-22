package gobby.gui.components.hud

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.gui.GuiGraphics

object HudDrawing {

    const val BACKGROUND_COLOR = 0x80101010.toInt()
    const val BORDER_COLOR = 0xFF303030.toInt()
    const val INNER_COLOR = 0x60000000.toInt()
    const val ACCENT_GREEN = 0xFF4CFF4C.toInt()
    const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    const val PANEL_PADDING = 4

    fun drawPanelBackground(ctx: GuiGraphics, width: Int, height: Int) {
        ctx.fill(0, 0, width, height, BACKGROUND_COLOR)
    }

    fun drawBoxWithBorder(ctx: GuiGraphics, x: Int, y: Int, width: Int, height: Int) {
        ctx.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER_COLOR)
        ctx.fill(x, y, x + width, y + height, INNER_COLOR)
    }

    fun drawSolidBox(ctx: GuiGraphics, x: Int, y: Int, width: Int, height: Int, fillColor: Int) {
        ctx.fill(x - 1, y - 1, x + width + 1, y + height + 1, BORDER_COLOR)
        ctx.fill(x, y, x + width, y + height, fillColor)
    }

    fun drawOutline(ctx: GuiGraphics, x: Int, y: Int, width: Int, height: Int, color: Int) {
        ctx.fill(x - 1, y - 1, x + width + 1, y, color)
        ctx.fill(x - 1, y + height, x + width + 1, y + height + 1, color)
        ctx.fill(x - 1, y - 1, x, y + height + 1, color)
        ctx.fill(x + width, y - 1, x + width + 1, y + height + 1, color)
    }

    fun drawCenteredText(ctx: GuiGraphics, text: String, x: Int, y: Int, width: Int, height: Int, color: Int = TEXT_COLOR) {
        val font = mc.font
        val textWidth = font.width(text)
        val textX = x + (width - textWidth) / 2
        val textY = y + (height - font.lineHeight) / 2 + 1
        ctx.drawString(font, text, textX, textY, color, true)
    }
}
