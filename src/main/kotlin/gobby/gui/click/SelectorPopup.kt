package gobby.gui.click

import gobby.utils.render.Animation
import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor

private const val OPTION_H = 15
private const val LIST_PAD = 4
private const val RADIUS = 5
private const val CHECK_W = 8
private const val TEXT_GAP = 4
private const val MIN_W = 70
private const val ROW_RADIUS = 3
private const val HOVER_INSET = 2
private const val ANCHOR_GAP = 2
private const val CHECK_ARM = 4
private const val REVEAL_MS = 170L
private const val MIN_REVEAL = 1

internal object SelectorPopup {

    private val reveal = Animation(REVEAL_MS)
    private var shown: SelectorSetting? = null

    fun sync(gui: ClickGUI) {
        val open = gui.openSelector
        if (open != null && open !== shown) {
            shown = open
            reveal.jumpTo(0f)
        }
        reveal.set(open != null)
        if (open == null && reveal.idle) shown = null
    }

    fun visible(): SelectorSetting? = shown

    fun forget() {
        shown = null
        reveal.jumpTo(0f)
    }

    fun bounds(gui: ClickGUI, row: PlacedRow, s: SelectorSetting): Rect {
        val widest = s.options.maxOfOrNull { textWScaled(it, SETTINGS_VALUE_SCALE) } ?: 0
        val w = (LIST_PAD * 2 + CHECK_W + TEXT_GAP + widest).coerceAtLeast(MIN_W)
        val h = LIST_PAD * 2 + s.options.size * OPTION_H
        val x = (row.x + row.w - w).coerceAtLeast(gui.panelX + SIDEBAR_W_SETTINGS + ANCHOR_GAP)
        val below = row.y + row.h + ANCHOR_GAP
        val y = if (below + h <= gui.panelY + PANEL_H) below else row.y - h - ANCHOR_GAP
        return Rect(x, y.coerceAtLeast(gui.panelY + SETTINGS_HEADER_H + ANCHOR_GAP), w, h)
    }

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, row: PlacedRow, mx: Int, my: Int) {
        val s = row.setting as? SelectorSetting ?: return
        val r = bounds(gui, row, s)
        val revealed = (r.h * reveal.value).toInt().coerceAtLeast(MIN_REVEAL)
        val clipTop = if (r.y >= row.y) r.y else r.y + r.h - revealed
        ctx.enableScissor(r.x, clipTop, r.x + r.w, clipTop + revealed)
        drawList(ctx, r, s, mx, my)
        ctx.disableScissor()
    }

    private fun drawList(ctx: GuiGraphicsExtractor, r: Rect, s: SelectorSetting, mx: Int, my: Int) {
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, RADIUS, cShellBg, cShellEdge)

        val textH = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        s.options.forEachIndexed { index, option ->
            val oy = r.y + LIST_PAD + index * OPTION_H
            val selected = index == s.value
            val hovered = my in oy until (oy + OPTION_H) && mx in r.x..(r.x + r.w)
            CursorStyle.requestHandIf(hovered)
            if (hovered) GobbyDraw.roundedRect(ctx, r.x + HOVER_INSET, oy, r.w - HOVER_INSET * 2, OPTION_H, ROW_RADIUS, cRowHover)
            if (selected) drawCheck(ctx, r.x + LIST_PAD, oy + OPTION_H / 2)
            val tint = if (selected) cInk else cInkSoft
            drawTextScaled(ctx, r.x + LIST_PAD + CHECK_W + TEXT_GAP, oy + (OPTION_H - textH) / 2, option, SETTINGS_VALUE_SCALE, tint, false)
        }
    }

    private fun drawCheck(ctx: GuiGraphicsExtractor, x: Int, centerY: Int) {
        ctx.fill(x + 1, centerY, x + 3, centerY + 2, cViolet)
        ctx.fill(x + 2, centerY + 1, x + 4, centerY + 3, cViolet)
        (0 until CHECK_ARM).forEach { step ->
            ctx.fill(x + 3 + step, centerY + 1 - step, x + 5 + step, centerY + 3 - step, cViolet)
        }
    }

    fun handleClick(gui: ClickGUI, row: PlacedRow, mx: Int, my: Int): Boolean {
        val s = row.setting as? SelectorSetting ?: return false
        val r = bounds(gui, row, s)
        if ((mx to my) !in r) {
            gui.openSelector = null
            return (mx to my) in Rect(row.x, row.y, row.w, row.h)
        }
        val index = (my - r.y - LIST_PAD) / OPTION_H
        if (index in s.options.indices) {
            s.value = index
            ConfigManager.save()
        }
        gui.openSelector = null
        return true
    }
}
