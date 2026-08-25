package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor as GuiGraphics

private const val PAD_X = 6
private const val PAD_Y = 4
private const val RADIUS = 4
private const val MAX_LINES = 6
private const val OVERHANG = 0.1f
private const val MIN_WIDTH = 80
private const val SCALE = 0.72f

object Tooltip {

    fun draw(ctx: GuiGraphics, gui: ClickGUI, text: String) {
        val maxRight = gui.panelX + PANEL_W + (PANEL_W * OVERHANG).toInt()
        val maxW = (maxRight - gui.tooltipX - PAD_X).coerceAtLeast(MIN_WIDTH)
        val lines = TextWrap.wrap(text, maxW - PAD_X * 2, SCALE, MAX_LINES)
        if (lines.isEmpty()) return

        val lineH = (tr.lineHeight * SCALE).toInt()
        val w = lines.maxOf { textWScaled(it, SCALE) } + PAD_X * 2
        val h = lines.size * lineH + PAD_Y * 2
        val x = gui.tooltipX.coerceAtMost(maxRight - w)
        val y = gui.tooltipY.coerceAtMost(gui.toGuiY(gui.height.toDouble()) - h - PAD_Y)

        GobbyDraw.roundedBox(ctx, x, y, w, h, RADIUS, cShellBg, cShellEdge)
        lines.forEachIndexed { index, line ->
            drawTextScaled(ctx, x + PAD_X, y + PAD_Y + index * lineH, line, SCALE, cInkSoft, false)
        }
    }
}
