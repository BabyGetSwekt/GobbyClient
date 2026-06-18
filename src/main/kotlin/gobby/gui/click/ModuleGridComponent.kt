package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor as GuiGraphics

object ModuleGridComponent {

    private const val DESC_SCALE = 0.7f
    private const val TITLE_MAX_LINES = 2
    private const val DESC_MAX_LINES = 5
    private const val CARD_TOGGLE_W = 18
    private const val CARD_TOGGLE_H = 8
    private const val CARD_LEFT_PAD = 8
    private const val CARD_RIGHT_PAD = 8
    private const val CARD_TOP_PAD = 6
    private const val CARD_BOTTOM_PAD = 8
    private const val TITLE_LINE_GAP = 1
    private const val TITLE_DESC_GAP = 4
    private const val DESC_LINE_GAP = 1
    private const val MIN_CARD_H = 50

    private fun cardWidth(gui: ClickGUI): Int = (gui.contentW - CARD_GAP * (GRID_COLS - 1)) / GRID_COLS

    private fun titleAreaWidth(cardW: Int): Int = cardW - CARD_LEFT_PAD - CARD_TOGGLE_W - CARD_RIGHT_PAD - 4

    private fun descLineHeight(): Int = TextWrap.scaledLineHeight(DESC_SCALE, DESC_LINE_GAP)

    private fun cardHeight(mod: Module, cardW: Int): Int {
        val titleLines = TextWrap.wrap(mod.name, titleAreaWidth(cardW), 1f, TITLE_MAX_LINES).size.coerceAtLeast(1)
        val descLines = if (mod.description.isEmpty()) 0
                        else TextWrap.wrap(mod.description, cardW - CARD_LEFT_PAD - CARD_RIGHT_PAD, DESC_SCALE, DESC_MAX_LINES).size
        val titleH = titleLines * tr.lineHeight + (titleLines - 1) * TITLE_LINE_GAP
        val descH = descLines * descLineHeight()
        val total = CARD_TOP_PAD + titleH + (if (descLines > 0) TITLE_DESC_GAP + descH else 0) + CARD_BOTTOM_PAD
        return maxOf(total, MIN_CARD_H)
    }

    private fun rowHeights(gui: ClickGUI): List<Int> {
        val cw = cardWidth(gui)
        return gui.visibleModules().chunked(GRID_COLS).map { row -> row.maxOf { cardHeight(it, cw) } }
    }

    private fun layout(gui: ClickGUI): List<Rect> {
        val cw = cardWidth(gui)
        val baseY = gui.contentY + gui.scrollOffset.toInt()
        val heights = rowHeights(gui)
        val rowYs = heights.runningFold(baseY) { acc, h -> acc + h + CARD_GAP }
        return gui.visibleModules().chunked(GRID_COLS).flatMapIndexed { rowIdx, row ->
            List(row.size) { col ->
                Rect(gui.contentX + col * (cw + CARD_GAP), rowYs[rowIdx], cw, heights[rowIdx])
            }
        }
    }

    private fun totalContentHeight(gui: ClickGUI): Int = rowHeights(gui).let {
        if (it.isEmpty()) 0 else it.sum() + (it.size - 1) * CARD_GAP
    }

    fun draw(ctx: GuiGraphics, gui: ClickGUI, mx: Int, my: Int) {
        val mods = gui.visibleModules()
        val clipTop = gui.contentY
        val clipBot = gui.contentY + gui.contentH

        ctx.enableScissor(gui.contentX - 4, clipTop, gui.contentX + gui.contentW + 4, clipBot)

        val rects = layout(gui)
        mods.forEachIndexed { i, mod ->
            val r = rects[i]
            if (r.y + r.h >= clipTop && r.y <= clipBot) drawCard(ctx, mod, r, mx, my)
        }

        if (mods.isEmpty()) {
            val text = "No modules matched your search. :("
            val w = tr.width(styledText(text))
            drawText(ctx, gui.contentX + (gui.contentW - w) / 2, gui.contentY + 30, text, cTextGray)
        }

        ctx.disableScissor()
        Scrollbar.draw(ctx, gui, totalContentHeight(gui))
    }

    private fun toggleRect(card: Rect) = Rect(card.x + card.w - CARD_TOGGLE_W - CARD_RIGHT_PAD, card.y + CARD_TOP_PAD, CARD_TOGGLE_W, CARD_TOGGLE_H)

    private fun drawCard(ctx: GuiGraphics, mod: Module, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        fill(ctx, r.x, r.y, r.w, r.h, if (hovered) cCardBgHov else cCardBg)
        if (mod.enabled) fill(ctx, r.x, r.y, 2, r.h, cEnabled)

        val nameCol = when {
            mod.enabled -> cAccent
            hovered -> cTextBright
            else -> cText
        }
        val titleLines = TextWrap.wrap(mod.name, titleAreaWidth(r.w), 1f, TITLE_MAX_LINES)
        titleLines.forEachIndexed { idx, line ->
            drawText(ctx, r.x + CARD_LEFT_PAD, r.y + CARD_TOP_PAD + idx * (tr.lineHeight + TITLE_LINE_GAP), line, nameCol)
        }

        if (mod.hasToggle && mod.toggled && !mod.isAlwaysEnabled) {
            val t = toggleRect(r)
            fill(ctx, t.x, t.y, t.w, t.h, if (mod.enabled) cToggleOn else cToggleOff)
            val knobX = if (mod.enabled) t.x + t.w - 7 else t.x + 1
            fill(ctx, knobX, t.y + 1, 6, t.h - 2, 0xFFFFFFFF.toInt())
        }

        if (mod.description.isNotEmpty()) {
            val descBaseY = r.y + CARD_TOP_PAD + titleLines.size * tr.lineHeight + (titleLines.size - 1) * TITLE_LINE_GAP + TITLE_DESC_GAP
            TextWrap.wrap(mod.description, r.w - CARD_LEFT_PAD - CARD_RIGHT_PAD, DESC_SCALE, DESC_MAX_LINES).forEachIndexed { idx, line ->
                drawTextScaled(ctx, r.x + CARD_LEFT_PAD, descBaseY + idx * descLineHeight(), line, DESC_SCALE, cTextGray)
            }
        }

        if (hovered && mod.enabled) fill(ctx, r.x, r.y + r.h - 1, r.w, 1, cAccentGlow)
    }

    fun handleClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean {
        if (my !in gui.contentY..(gui.contentY + gui.contentH)) return false
        val pos = mx to my
        val rects = layout(gui)
        val mods = gui.visibleModules()

        mods.forEachIndexed { i, mod ->
            val r = rects[i]
            if (pos !in r) return@forEachIndexed
            val canToggle = mod.hasToggle && mod.toggled && !mod.isAlwaysEnabled
            when (button) {
                0 -> if (canToggle) { mod.enabled = !mod.enabled; ConfigManager.save() }
                1 -> gui.openSettings(mod)
            }
            return true
        }
        return false
    }

    fun handleScroll(gui: ClickGUI, mx: Int, my: Int, vAmt: Double): Boolean {
        if (mx !in gui.contentX..(gui.contentX + gui.contentW)) return false
        if (my !in gui.contentY..(gui.contentY + gui.contentH)) return false
        val maxOffset = (totalContentHeight(gui) - gui.contentH).coerceAtLeast(0).toFloat()
        gui.scrollTarget = (gui.scrollTarget + vAmt.toFloat() * 24f).coerceIn(-maxOffset, 0f)
        return true
    }

}
