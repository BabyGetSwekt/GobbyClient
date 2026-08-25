package gobby.gui.click

import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor as GuiGraphics

private const val COLUMNS = 2
private const val CARD_PAD = 9
private const val CARD_RADIUS = 6
private const val CARD_GAP_Y = 7
private const val CARD_GAP_X = 9
private const val TITLE_SCALE = 0.86f
private const val DESC_SCALE = 0.66f
private const val DESC_MAX_LINES = 3
private const val TITLE_DESC_GAP = 3
private const val DESC_LINE_GAP = 1
private const val BADGE_SLOT = 11
private const val CONTROL_GAP = 7
private const val SCROLL_STEP = 26f
private const val EMPTY_TOP = 30
private const val SCROLL_TAIL = 6

object ModuleGridComponent {

    private fun cardWidth(gui: ClickGUI): Int = (gui.contentW - CARD_GAP_X * (COLUMNS - 1)) / COLUMNS

    private fun controlsWidth(mod: Module): Int =
        BADGE_SLOT + CONTROL_GAP + if (mod.canToggle()) PILL_W else 0

    private fun textWidth(mod: Module, cardW: Int): Int =
        cardW - CARD_PAD * 2 - controlsWidth(mod).let { if (it > 0) it + CONTROL_GAP else 0 }

    private fun descLines(mod: Module, cardW: Int): List<String> =
        if (mod.description.isEmpty()) emptyList()
        else TextWrap.wrap(mod.description, textWidth(mod, cardW), DESC_SCALE, DESC_MAX_LINES)

    private fun cardHeight(mod: Module, cardW: Int): Int {
        val titleH = (tr.lineHeight * TITLE_SCALE).toInt()
        val lines = descLines(mod, cardW)
        val descH = lines.size * TextWrap.scaledLineHeight(DESC_SCALE, DESC_LINE_GAP)
        return CARD_PAD * 2 + titleH + if (lines.isEmpty()) 0 else TITLE_DESC_GAP + descH
    }

    private fun rowHeights(gui: ClickGUI): List<Int> {
        val cw = cardWidth(gui)
        return gui.visibleModules().chunked(COLUMNS).map { row -> row.maxOf { cardHeight(it, cw) } }
    }

    private fun layout(gui: ClickGUI): List<Rect> {
        val cw = cardWidth(gui)
        val heights = rowHeights(gui)
        val rowTops = heights.runningFold(gui.contentY + gui.scrollOffset.toInt()) { acc, h -> acc + h + CARD_GAP_Y }
        return gui.visibleModules().chunked(COLUMNS).flatMapIndexed { rowIndex, row ->
            row.indices.map { col -> Rect(gui.contentX + col * (cw + CARD_GAP_X), rowTops[rowIndex], cw, heights[rowIndex]) }
        }
    }

    private fun totalContentHeight(gui: ClickGUI): Int = rowHeights(gui).let {
        if (it.isEmpty()) 0 else it.sum() + (it.size - 1) * CARD_GAP_Y + SCROLL_TAIL
    }

    private fun togglePill(card: Rect) =
        Rect(card.x + card.w - CARD_PAD - PILL_W, card.y + (card.h - PILL_H) / 2, PILL_W, PILL_H)

    fun draw(ctx: GuiGraphics, gui: ClickGUI, mx: Int, my: Int) {
        val top = gui.contentY
        val bottom = gui.contentY + gui.contentH
        ctx.enableScissor(gui.contentX, top, gui.contentX + gui.contentW, bottom)
        val rects = layout(gui)
        gui.visibleModules().forEachIndexed { index, mod ->
            val r = rects[index]
            if (r.y + r.h >= top && r.y <= bottom) drawCard(ctx, mod, r, mx, my)
        }
        ctx.disableScissor()

        if (gui.visibleModules().isEmpty()) drawEmpty(ctx, gui)
        Scrollbar.draw(ctx, gui, totalContentHeight(gui))
    }

    private fun drawEmpty(ctx: GuiGraphics, gui: ClickGUI) {
        val text = "No modules matched your search"
        val w = textWScaled(text, SETTINGS_VALUE_SCALE)
        drawTextScaled(ctx, gui.contentX + (gui.contentW - w) / 2, gui.contentY + EMPTY_TOP, text, SETTINGS_VALUE_SCALE, cInkGhost, false)
    }

    private fun drawCard(ctx: GuiGraphics, mod: Module, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        val border = if (mod.enabled) cViolet else cCardEdge
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, CARD_RADIUS, if (hovered) cIconTile else cCard, border)

        val titleH = (tr.lineHeight * TITLE_SCALE).toInt()
        val titleColor = if (mod.enabled) cInk else cInkSoft
        drawTextScaled(ctx, r.x + CARD_PAD, r.y + CARD_PAD, mod.name, TITLE_SCALE, titleColor, false)

        if (mod.canToggle()) SettingsControls.pill(ctx, togglePill(r), mod.enabled)

        val lines = descLines(mod, r.w)
        val descTop = r.y + CARD_PAD + titleH + TITLE_DESC_GAP
        lines.forEachIndexed { index, line ->
            drawTextScaled(ctx, r.x + CARD_PAD, descTop + index * TextWrap.scaledLineHeight(DESC_SCALE, DESC_LINE_GAP), line, DESC_SCALE, cInkFaint, false)
        }
    }

    fun handleClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean {
        if (my !in gui.contentY..(gui.contentY + gui.contentH)) return false
        val rects = layout(gui)
        gui.visibleModules().forEachIndexed { index, mod ->
            val r = rects[index]
            if ((mx to my) !in r) return@forEachIndexed
            if (mod.canToggle() && (mx to my) in togglePill(r)) {
                mod.enabled = !mod.enabled
                ConfigManager.save()
            } else {
                gui.openSettings(mod)
            }
            return true
        }
        return false
    }

    fun handleScroll(gui: ClickGUI, mx: Int, my: Int, vAmt: Double): Boolean {
        if (mx !in gui.contentX..(gui.contentX + gui.contentW)) return false
        if (my !in gui.contentY..(gui.contentY + gui.contentH)) return false
        val maxOffset = (totalContentHeight(gui) - gui.contentH).coerceAtLeast(0).toFloat()
        gui.scrollTarget = (gui.scrollTarget + vAmt.toFloat() * SCROLL_STEP).coerceIn(-maxOffset, 0f)
        return true
    }
}
