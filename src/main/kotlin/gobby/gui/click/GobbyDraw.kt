package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val FULL_ALPHA = 255
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF
private const val BORDER = 1

object GobbyDraw {


    fun alpha(color: Int, factor: Float): Int {
        val a = ((color ushr ALPHA_SHIFT and CHANNEL_MASK) * factor).roundToInt().coerceIn(0, FULL_ALPHA)
        return (a shl ALPHA_SHIFT) or (color and 0x00FFFFFF)
    }

    fun mix(from: Int, to: Int, progress: Float): Int {
        val t = progress.coerceIn(0f, 1f)
        return channels(from).zip(channels(to)) { a, b -> a + ((b - a) * t).roundToInt() }.let(::pack)
    }

    fun roundedRect(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int) =
        GobbyTextures.roundedRect(ctx, x, y, w, h, radius, color)

    fun roundedBox(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, fill: Int, border: Int) {
        GobbyTextures.roundedRect(ctx, x, y, w, h, radius, border)
        GobbyTextures.roundedRect(ctx, x + BORDER, y + BORDER, w - BORDER * 2, h - BORDER * 2, radius - BORDER, fill)
    }

    fun roundedOutline(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int) {
        val r = radius.coerceAtMost(minOf(w, h) / 2)
        ctx.fill(x + r, y, x + w - r, y + 1, color)
        ctx.fill(x + r, y + h - 1, x + w - r, y + h, color)
        ctx.fill(x, y + r, x + 1, y + h - r, color)
        ctx.fill(x + w - 1, y + r, x + w, y + h - r, color)
        for (row in 0 until r) {
            val inset = r - sqrt((r * r - (r - row - 1) * (r - row - 1)).toFloat()).roundToInt()
            ctx.fill(x + inset, y + row, x + inset + 1, y + row + 1, color)
            ctx.fill(x + w - inset - 1, y + row, x + w - inset, y + row + 1, color)
            ctx.fill(x + inset, y + h - row - 1, x + inset + 1, y + h - row, color)
            ctx.fill(x + w - inset - 1, y + h - row - 1, x + w - inset, y + h - row, color)
        }
    }

    fun verticalGradient(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, top: Int, bottom: Int) {
        ctx.fillGradient(x, y, x + w, y + h, top, bottom)
    }

    fun horizontalGradient(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, left: Int, right: Int) {
        for (column in 0 until w) {
            ctx.fill(x + column, y, x + column + 1, y + h, mix(left, right, column.toFloat() / w.coerceAtLeast(1)))
        }
    }

    private fun channels(color: Int) = listOf(
        color ushr ALPHA_SHIFT and CHANNEL_MASK,
        color ushr RED_SHIFT and CHANNEL_MASK,
        color ushr GREEN_SHIFT and CHANNEL_MASK,
        color and CHANNEL_MASK
    )

    private fun pack(parts: List<Int>) =
        (parts[0] shl ALPHA_SHIFT) or (parts[1] shl RED_SHIFT) or (parts[2] shl GREEN_SHIFT) or parts[3]
}
