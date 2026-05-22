package gobby.gui.components.hud

import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.GuiGraphics

object KeystrokesHud {

    private const val KEY_SIZE = 20
    private const val KEY_GAP = 2
    private const val ROW_GAP = 2
    private const val KEY_STRIDE = KEY_SIZE + KEY_GAP
    private const val ROW_STRIDE = KEY_SIZE + ROW_GAP

    data class Size(val width: Int, val height: Int)

    data class KeyBindings(
        val forward: KeyMapping,
        val left: KeyMapping,
        val back: KeyMapping,
        val right: KeyMapping
    )

    fun renderKeystrokes(ctx: GuiGraphics, bindings: KeyBindings, exampleMode: Boolean): Size {
        val pad = HudDrawing.PANEL_PADDING
        val bottomRowWidth = KEY_STRIDE * 3 - KEY_GAP
        val topKeyWidth = KEY_SIZE
        val totalWidth = bottomRowWidth + pad * 2
        val totalHeight = KEY_SIZE * 2 + ROW_GAP + pad * 2

        val originX = pad
        val originY = pad
        val forwardX = originX + KEY_STRIDE
        val rowY = originY + ROW_STRIDE

        val painted = mutableListOf<IntArray>()
        drawClusterBackground(ctx, forwardX - pad, originY - pad, topKeyWidth + pad * 2, KEY_SIZE + pad * 2, painted)
        drawClusterBackground(ctx, originX - pad, rowY - pad, bottomRowWidth + pad * 2, KEY_SIZE + pad * 2, painted)

        drawKeyAt(ctx, forwardX, originY, bindings.forward, exampleMode)
        drawKeyAt(ctx, originX, rowY, bindings.left, exampleMode)
        drawKeyAt(ctx, originX + KEY_STRIDE, rowY, bindings.back, exampleMode)
        drawKeyAt(ctx, originX + KEY_STRIDE * 2, rowY, bindings.right, exampleMode)

        return Size(totalWidth, totalHeight)
    }

    private fun drawClusterBackground(ctx: GuiGraphics, x: Int, y: Int, width: Int, height: Int, painted: MutableList<IntArray>) {
        var pieces = listOf(intArrayOf(x, y, x + width, y + height))
        for (p in painted) pieces = pieces.flatMap { subtractRect(it, p) }
        for (piece in pieces) {
            ctx.fill(piece[0], piece[1], piece[2], piece[3], HudDrawing.BACKGROUND_COLOR)
            painted.add(piece)
        }
    }

    private fun subtractRect(a: IntArray, b: IntArray): List<IntArray> {
        val ax1 = a[0]; val ay1 = a[1]; val ax2 = a[2]; val ay2 = a[3]
        val bx1 = b[0]; val by1 = b[1]; val bx2 = b[2]; val by2 = b[3]
        if (ax2 <= bx1 || bx2 <= ax1 || ay2 <= by1 || by2 <= ay1) return listOf(a)
        val out = mutableListOf<IntArray>()
        if (ay1 < by1) out.add(intArrayOf(ax1, ay1, ax2, by1))
        if (ay2 > by2) out.add(intArrayOf(ax1, by2, ax2, ay2))
        val midY1 = maxOf(ay1, by1)
        val midY2 = minOf(ay2, by2)
        if (midY1 < midY2) {
            if (ax1 < bx1) out.add(intArrayOf(ax1, midY1, bx1, midY2))
            if (ax2 > bx2) out.add(intArrayOf(bx2, midY1, ax2, midY2))
        }
        return out
    }

    private fun drawKeyAt(ctx: GuiGraphics, x: Int, y: Int, key: KeyMapping, exampleMode: Boolean) {
        val pressed = !exampleMode && key.isDown
        HudDrawing.drawBoxWithBorder(ctx, x, y, KEY_SIZE, KEY_SIZE)
        if (pressed) HudDrawing.drawOutline(ctx, x, y, KEY_SIZE, KEY_SIZE, HudDrawing.ACCENT_GREEN)
        val label = keyLabel(key)
        val color = if (pressed) HudDrawing.ACCENT_GREEN else HudDrawing.TEXT_COLOR
        HudDrawing.drawCenteredText(ctx, label, x, y, KEY_SIZE, KEY_SIZE, color)
    }

    private fun keyLabel(key: KeyMapping): String {
        val raw = key.translatedKeyMessage.string
        return when {
            raw.length <= 3 -> raw
            else -> raw.first().uppercase()
        }
    }
}
