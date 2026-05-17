package gobby.gui.click

import net.minecraft.client.gui.GuiGraphics

object SearchComponent {

    private const val SEARCH_W = 160
    private const val SEARCH_H = 18
    private const val SEARCH_MARGIN = 8

    private fun searchBarBounds(gui: ClickGUI) = Rect(
        gui.panelX + PANEL_W - SEARCH_W - SEARCH_MARGIN,
        gui.panelY + (HEADER_H - SEARCH_H) / 2,
        SEARCH_W, SEARCH_H
    )

    private const val BACK_W = 22
    private const val BACK_H = 18

    private fun backButtonBounds(gui: ClickGUI) = Rect(
        gui.panelX + SIDEBAR_W_COLLAPSED + 8,
        gui.panelY + (HEADER_H - BACK_H) / 2,
        BACK_W, BACK_H
    )

    fun draw(ctx: GuiGraphics, gui: ClickGUI, mx: Int, my: Int) {
        val mod = gui.settingsModule
        val crumbX = gui.panelX + SIDEBAR_W_COLLAPSED + 12
        val crumbY = gui.panelY + (HEADER_H - tr.lineHeight) / 2

        if (mod == null) {
            drawText(ctx, crumbX, crumbY, gui.currentCategory.displayName, cTextBright)
        } else {
            drawBackButton(ctx, gui, mx, my)
            val titleX = backButtonBounds(gui).let { it.x + it.w + 10 }
            drawText(ctx, titleX, crumbY, "${gui.currentCategory.displayName}  /  ${mod.name}", cTextBright)
        }

        val sr = searchBarBounds(gui)
        fill(ctx, sr.x, sr.y, sr.w, sr.h, cSearchBg)
        drawBorder(ctx, sr.x, sr.y, sr.w, sr.h, if (gui.searchFocused) cAccent else cBorder)

        val pad = 6
        val textY = sr.y + (sr.h - tr.lineHeight) / 2
        val showPlaceholder = gui.searchQuery.isEmpty() && !gui.searchFocused
        if (showPlaceholder) {
            drawText(ctx, sr.x + pad, textY, "search...", cTextGray)
        } else {
            val baseCol = if (gui.searchSelectAll) cTextBright else cText
            val display = trimToTrailing(gui.searchQuery, sr.w - pad * 2)
            val queryW = tr.width(styledText(display))
            if (gui.searchSelectAll) fill(ctx, sr.x + pad - 1, textY - 1, queryW + 2, tr.lineHeight + 1, cAccent)
            drawText(ctx, sr.x + pad, textY, display, baseCol)
            if (gui.searchFocused && (System.currentTimeMillis() / 500) % 2 == 0L)
                fill(ctx, sr.x + pad + queryW, textY, 1, tr.lineHeight, cAccent)
        }
    }

    private fun trimToTrailing(text: String, maxW: Int): String {
        if (tr.width(styledText(text)) <= maxW) return text
        var i = 1
        while (i < text.length && tr.width(styledText(text.substring(i))) > maxW) i++
        return text.substring(i)
    }

    private fun drawBackButton(ctx: GuiGraphics, gui: ClickGUI, mx: Int, my: Int) {
        val r = backButtonBounds(gui)
        val hovered = (mx to my) in r
        val bg = if (hovered) cAccentDim else cKeyBox
        val border = if (hovered) cAccent else cBorder
        val iconCol = if (hovered) cAccent else cText
        fill(ctx, r.x, r.y, r.w, r.h, bg)
        drawBorder(ctx, r.x, r.y, r.w, r.h, border)
        val arrow = "←"
        val arrowW = tr.width(styledText(arrow))
        drawText(ctx, r.x + (r.w - arrowW) / 2, r.y + (r.h - tr.lineHeight) / 2, arrow, iconCol)
    }

    fun handleClick(gui: ClickGUI, mx: Int, my: Int): Boolean {
        if ((mx to my) in searchBarBounds(gui)) {
            gui.searchFocused = true
            gui.searchSelectAll = gui.searchQuery.isNotEmpty()
            return true
        }
        gui.searchFocused = false
        gui.searchSelectAll = false

        if (gui.settingsModule != null && (mx to my) in backButtonBounds(gui)) {
            gui.closeSettings()
            return true
        }
        return false
    }

    fun drawTooltip(ctx: GuiGraphics, gui: ClickGUI, text: String) {
        val screenBottom = gui.toGuiY(gui.height.toDouble())
        val maxRight = gui.panelX + PANEL_W + (PANEL_W * 0.1f).toInt()
        val maxW = (maxRight - gui.tooltipX - 4).coerceAtLeast(80)
        val lines = TextWrap.wrap(text, maxW - 8, 1f, 6)
        if (lines.isEmpty()) return
        val w = lines.maxOf { tr.width(styledText(it)) } + 8
        val h = lines.size * tr.lineHeight + 6
        val x = gui.tooltipX.coerceAtMost(maxRight - w)
        val y = gui.tooltipY.coerceAtMost(screenBottom - h - 2)
        fill(ctx, x, y, w, h, cTooltipBg)
        drawBorder(ctx, x, y, w, h, cBorderLight)
        lines.forEachIndexed { i, line ->
            drawText(ctx, x + 4, y + 3 + i * tr.lineHeight, line, cText)
        }
    }
}
