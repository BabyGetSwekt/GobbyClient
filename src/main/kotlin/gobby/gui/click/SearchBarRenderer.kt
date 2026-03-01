package gobby.gui.click

import gobby.gui.click.ClickGUITheme.cAccent
import gobby.gui.click.ClickGUITheme.cBorder
import gobby.gui.click.ClickGUITheme.cSearchBg
import gobby.gui.click.ClickGUITheme.cText
import gobby.gui.click.ClickGUITheme.cTextBright
import gobby.gui.click.ClickGUITheme.cTextDark
import gobby.gui.click.ClickGUITheme.cTooltipBg
import gobby.gui.click.ClickGUITheme.tr
import net.minecraft.client.gui.DrawContext

object SearchBarRenderer {

    const val SEARCH_W = 200
    const val SEARCH_H = 18

    fun drawSearchBar(ctx: DrawContext, gui: ClickGUI, mx: Int, my: Int) {
        val sx = (gui.width - SEARCH_W) / 2
        val sy = gui.height - 24

        ClickGUITheme.fill(ctx, sx, sy, SEARCH_W, SEARCH_H, cSearchBg)
        ClickGUITheme.drawBorder(ctx, sx, sy, SEARCH_W, SEARCH_H, if (gui.searchListening) cAccent else cBorder)

        val textY = sy + (SEARCH_H - tr.fontHeight) / 2
        var textX = sx + 6

        if (gui.searchListening) {
            ClickGUITheme.drawText(ctx, textX, textY, "> ", cAccent)
            textX += ClickGUITheme.textW("> ")
        }

        val display = if (gui.searchQuery.isEmpty()) {
            if (gui.searchListening) "" else "Search..."
        } else gui.searchQuery
        val textCol = if (gui.searchQuery.isEmpty() && !gui.searchListening) cTextDark else cText
        ClickGUITheme.drawText(ctx, textX, textY, display, textCol)

        if (gui.searchListening && (System.currentTimeMillis() / 500) % 2 == 0L) {
            val cursorX = textX + ClickGUITheme.textW(gui.searchQuery)
            ClickGUITheme.fill(ctx, cursorX, sy + 5, 1, SEARCH_H - 10, cText)
        }
    }

    private const val TOOLTIP_MAX_W = 150

    fun drawTooltip(ctx: DrawContext, gui: ClickGUI, text: String) {
        val wrapped = wrapText(text, TOOLTIP_MAX_W)
        val maxW = wrapped.maxOf { ClickGUITheme.textWSmall(it) }
        val padH = 5
        val padW = 8
        val lineH = (tr.fontHeight * ClickGUITheme.SETTING_SCALE).toInt() + 2
        val totalH = wrapped.size * lineH + padH * 2
        val totalW = maxW + padW * 2 + 3

        val tx = gui.tooltipX.coerceIn(4, gui.width - totalW - 4)
        val ty = gui.tooltipY.coerceIn(4, gui.height - totalH - 4)

        ClickGUITheme.fill(ctx, tx, ty, totalW, totalH, cTooltipBg)
        ClickGUITheme.drawBorder(ctx, tx, ty, totalW, totalH, cBorder)

        // Left accent line
        ClickGUITheme.fill(ctx, tx, ty, 3, totalH, cAccent)

        for ((i, line) in wrapped.withIndex()) {
            ClickGUITheme.drawTextSmall(ctx, tx + padW + 3, ty + padH + i * lineH, line, cText)
        }
    }

    private fun wrapText(text: String, maxWidth: Int): List<String> {
        val result = mutableListOf<String>()
        for (line in text.split("\n")) {
            val words = line.split(" ")
            var current = ""
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (ClickGUITheme.textWSmall(test) > maxWidth && current.isNotEmpty()) {
                    result.add(current)
                    current = word
                } else {
                    current = test
                }
            }
            if (current.isNotEmpty()) result.add(current)
        }
        return result.ifEmpty { listOf("") }
    }
}
