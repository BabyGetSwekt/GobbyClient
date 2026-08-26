package gobby.gui.screen.mobesp

import gobby.features.render.MobEsp
import gobby.gui.click.*

import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW

private const val ROW_H = 24
private const val ROW_GAP = 4
private const val ROW_RADIUS = 5
private const val ROW_PAD = 8
private const val CHECK_SIZE = 13
private const val COL_GAP = 8
private const val FILTER_W = 62
private const val FILTER_H = 15
private const val SWATCH = 15
private const val REMOVE_W = 14
private const val ADD_H = 20
private const val ADD_RADIUS = 5
private const val PLUS_ICON = 7
private const val ADD_SHARE = 3
private const val BAR_PARTS = 4
private const val SEARCH_ICON = 11
private const val SEARCH_PAD = 6
private const val SEARCH_HINT = "Search"
private const val SCROLL_STEP = 26f
private const val SCROLL_TAIL = 6
private const val EMPTY_TOP = 18
private const val NAME_MIN_W = 40
private const val ADD_LABEL = "Add mob"
private const val EMPTY_LABEL = "No mobs yet. Add one to start highlighting."
private const val HINT = "equals matches the whole name, contains matches part of it"
private const val TRASH_ICON = 10

internal object MobEspView : ClickView {

    override fun onOpened() = MobEspList.load()

    override fun onClosed() = MobEspList.close()

    private var shownQuery = ""

    private fun addRect(gui: ClickGUI) =
        Rect(gui.contentX, gui.contentY, (gui.contentW - COL_GAP) * ADD_SHARE / BAR_PARTS, ADD_H)

    private fun searchRect(gui: ClickGUI): Rect {
        val left = addRect(gui).let { it.x + it.w + COL_GAP }
        return Rect(left, gui.contentY, gui.contentX + gui.contentW - left, ADD_H)
    }

    private fun rowRect(gui: ClickGUI, index: Int): Rect {
        val top = gui.contentY + ADD_H + ROW_GAP + index * (ROW_H + ROW_GAP) + gui.scrollOffset.toInt()
        return Rect(gui.contentX, top, gui.contentW, ROW_H)
    }

    private fun totalHeight(): Int =
        ADD_H + ROW_GAP + MobEspList.visibleRows().size * (ROW_H + ROW_GAP) + SCROLL_TAIL

    private fun checkRect(r: Rect) = Rect(r.x + ROW_PAD, r.y + (r.h - CHECK_SIZE) / 2, CHECK_SIZE, CHECK_SIZE)

    private fun removeRect(r: Rect) = Rect(r.x + r.w - ROW_PAD - REMOVE_W, r.y + (r.h - REMOVE_W) / 2, REMOVE_W, REMOVE_W)

    private fun swatchRect(r: Rect) =
        Rect(removeRect(r).x - COL_GAP - SWATCH, r.y + (r.h - SWATCH) / 2, SWATCH, SWATCH)

    private fun filterRect(r: Rect) =
        Rect(swatchRect(r).x - COL_GAP - FILTER_W, r.y + (r.h - FILTER_H) / 2, FILTER_W, FILTER_H)

    private fun nameRect(r: Rect): Rect {
        val left = checkRect(r).let { it.x + it.w + COL_GAP }
        return Rect(left, r.y, (filterRect(r).x - COL_GAP - left).coerceAtLeast(NAME_MIN_W), r.h)
    }

    override fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        SettingsHeader.draw(ctx, gui, MobEsp.category.iconTexture, MobEsp.name, HINT, mx, my, SettingsHeader.cancelIcon())
        followSearch(gui)
        gui.clampScroll(totalHeight(), gui.contentH)

        val bottom = gui.contentY + gui.contentH
        ctx.enableScissor(gui.contentX, gui.contentY, gui.contentX + gui.contentW, bottom)
        drawAdd(ctx, gui, mx, my)
        MobEspList.visibleRows().forEachIndexed { index, row ->
            val r = rowRect(gui, index)
            if (r.y + r.h >= gui.contentY && r.y <= bottom) drawRow(ctx, row, r, mx, my)
        }
        ctx.disableScissor()

        drawSearch(ctx, gui, mx, my)
        if (MobEspList.visibleRows().isEmpty()) drawEmpty(ctx, gui)
        Scrollbar.draw(ctx, gui, totalHeight())
        expandedRow(gui)?.let { (row, r) ->
            ColorPickerPopup.draw(ctx, gui, PlacedRow(row.color, r.x, r.y, r.w, r.h))
        }
    }

    private fun followSearch(gui: ClickGUI) {
        if (MobEspList.searchField.text == shownQuery) return
        shownQuery = MobEspList.searchField.text
        gui.resetScroll()
    }

    private fun drawAdd(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val r = addRect(gui)
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, ADD_RADIUS, if (hovered) cViolet else cValueBox)
        val labelW = textWScaled(ADD_LABEL, SETTINGS_VALUE_SCALE)
        val plusX = r.x + (r.w - labelW - PLUS_ICON - COL_GAP) / 2
        GobbyTextures.plus(ctx, plusX, r.y + (r.h - PLUS_ICON) / 2, PLUS_ICON, cInk)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, plusX + PLUS_ICON + COL_GAP, r.y + (r.h - h) / 2, ADD_LABEL, SETTINGS_VALUE_SCALE, cInk, false)
    }

    private fun drawSearch(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val r = searchRect(gui)
        val focused = MobEspList.searchFocused
        CursorStyle.requestHandIf((mx to my) in r)
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, ADD_RADIUS, cValueBox, if (focused) cViolet else cCardEdge)
        GobbyTextures.search(ctx, r.x + SEARCH_PAD, r.y + (r.h - SEARCH_ICON) / 2, SEARCH_ICON, if (focused) cInkSoft else cInkFaint)
        val textX = r.x + SEARCH_PAD + SEARCH_ICON + SEARCH_PAD
        ctx.enableScissor(textX, r.y, r.x + r.w - SEARCH_PAD, r.y + r.h)
        TextFieldView.draw(
            ctx, MobEspList.searchField, textX, r.y, r.h, SETTINGS_VALUE_SCALE, cInk, focused,
            placeholder = SEARCH_HINT
        )
        ctx.disableScissor()
    }

    private fun drawRow(ctx: GuiGraphicsExtractor, row: MobRow, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, ROW_RADIUS, if (hovered) cIconTile else cCard, cCardEdge)
        drawCheck(ctx, row, checkRect(r), mx, my)
        drawName(ctx, row, nameRect(r), mx, my)
        drawFilter(ctx, row, filterRect(r), mx, my)
        drawSwatch(ctx, row, swatchRect(r), mx, my)
        drawRemove(ctx, removeRect(r), mx, my)
    }

    private fun drawCheck(ctx: GuiGraphicsExtractor, row: MobRow, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        val tint = if (row.entry.enabled) cViolet else if (hovered) cInkSoft else cInkFaint
        GobbyTextures.checkbox(ctx, r.x, r.y, r.w, row.entry.enabled, tint)
    }

    private fun drawName(ctx: GuiGraphicsExtractor, row: MobRow, r: Rect, mx: Int, my: Int) {
        val editing = MobEspList.editing === row
        CursorStyle.requestHandIf((mx to my) in r)
        val shown = MobEspList.nameOf(row)
        val tint = if (shown.isEmpty() && !editing) cInkGhost else cInk
        TextFieldView.draw(
            ctx, MobEspList.nameField, r.x, r.y, r.h, SETTINGS_VALUE_SCALE, tint, editing,
            shown = shown, placeholder = if (editing) "" else "Click to name this mob"
        )
    }

    private fun drawFilter(ctx: GuiGraphicsExtractor, row: MobRow, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, ADD_RADIUS, cValueBox, if (hovered) cViolet else cValueBox)
        val label = row.entry.filter.label
        val w = textWScaled(label, SETTINGS_VALUE_SCALE)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, r.x + (r.w - w) / 2, r.y + (r.h - h) / 2, label, SETTINGS_VALUE_SCALE, cInkSoft, false)
    }

    private fun drawSwatch(ctx: GuiGraphicsExtractor, row: MobRow, r: Rect, mx: Int, my: Int) {
        CursorStyle.requestHandIf((mx to my) in r)
        GobbyDraw.roundedRect(ctx, r.x - 1, r.y - 1, r.w + 2, r.h + 2, ADD_RADIUS, cCardEdge)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, ADD_RADIUS, row.color.value.rgb or OPAQUE_BITS)
    }

    private fun drawRemove(ctx: GuiGraphicsExtractor, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, ADD_RADIUS, if (hovered) cInvalid else cValueBox)
        GobbyTextures.trash(
            ctx, r.x + (r.w - TRASH_ICON) / 2, r.y + (r.h - TRASH_ICON) / 2, TRASH_ICON,
            if (hovered) cInk else cInkSoft
        )
    }

    private fun drawEmpty(ctx: GuiGraphicsExtractor, gui: ClickGUI) {
        val w = textWScaled(EMPTY_LABEL, SETTINGS_VALUE_SCALE)
        drawTextScaled(
            ctx, gui.contentX + (gui.contentW - w) / 2, gui.contentY + ADD_H + EMPTY_TOP,
            EMPTY_LABEL, SETTINGS_VALUE_SCALE, cInkGhost, false
        )
    }

    private fun expandedRow(gui: ClickGUI): Pair<MobRow, Rect>? =
        MobEspList.visibleRows().withIndex().firstOrNull { it.value.color.expanded }
            ?.let { it.value to rowRect(gui, it.index) }

    override fun handleClick(gui: ClickGUI, mx: Int, my: Int): Boolean {
        expandedRow(gui)?.let { (row, r) ->
            if (ColorPickerPopup.handleClick(gui, PlacedRow(row.color, r.x, r.y, r.w, r.h), mx, my)) {
                MobEspList.commit()
                return true
            }
        }
        if ((mx to my) in SettingsHeader.backRect(gui)) {
            gui.dismissView()
            return true
        }
        if ((mx to my) in searchRect(gui)) {
            MobEspList.focusSearch()
            val field = MobEspList.searchField
            field.placeCaret(TextFieldView.caretIndexAt(field.text, searchTextX(gui), mx, SETTINGS_VALUE_SCALE), extend = false)
            return true
        }
        MobEspList.blurSearch()
        blurNameUnlessClicked(gui, mx, my)
        if ((mx to my) in addRect(gui)) {
            MobEspList.stopEditing()
            MobEspList.add()
            return true
        }
        return clickRow(gui, mx, my)
    }

    private fun searchTextX(gui: ClickGUI): Int = searchRect(gui).x + SEARCH_PAD + SEARCH_ICON + SEARCH_PAD

    private fun blurNameUnlessClicked(gui: ClickGUI, mx: Int, my: Int) {
        val editing = MobEspList.editing ?: return
        val index = MobEspList.visibleRows().indexOf(editing)
        if (index >= 0 && (mx to my) in nameRect(rowRect(gui, index))) return
        MobEspList.stopEditing()
    }

    private fun clickRow(gui: ClickGUI, mx: Int, my: Int): Boolean {
        val hit = MobEspList.visibleRows().withIndex().firstOrNull { (mx to my) in rowRect(gui, it.index) } ?: return false
        val row = hit.value
        val r = rowRect(gui, hit.index)
        when {
            (mx to my) in removeRect(r) -> MobEspList.remove(row)
            (mx to my) in swatchRect(r) -> {
                MobEspList.stopEditing()
                row.color.expanded = !row.color.expanded
            }
            (mx to my) in filterRect(r) -> MobEspList.cycleFilter(row)
            (mx to my) in checkRect(r) -> MobEspList.toggle(row)
            else -> startNameEdit(row, nameRect(r), mx)
        }
        return true
    }

    private fun startNameEdit(row: MobRow, r: Rect, mx: Int) {
        MobEspList.startEditing(row)
        val index = TextFieldView.caretIndexAt(MobEspList.nameField.text, r.x, mx, SETTINGS_VALUE_SCALE)
        MobEspList.nameField.placeCaret(index, extend = false)
    }

    override fun handleScroll(gui: ClickGUI, mx: Int, my: Int, amount: Double): Boolean {
        if (mx !in gui.contentX..(gui.contentX + gui.contentW)) return false
        gui.scrollTarget = ScrollBounds.clamp(
            gui.scrollTarget + amount.toFloat() * SCROLL_STEP, totalHeight(), gui.contentH
        )
        return true
    }

    override fun handleKey(gui: ClickGUI, key: Int): Boolean {
        if (MobEspList.searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                MobEspList.blurSearch()
                return true
            }
            return TextFieldKeys.handle(MobEspList.searchField, key)
        }
        val editing = MobEspList.editing
        if (editing == null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) return false
            gui.dismissView()
            return true
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            MobEspList.stopEditing()
            return true
        }
        return TextFieldKeys.handle(MobEspList.nameField, key)
    }

    override fun handleChar(chr: Char): Boolean {
        if (MobEspList.searchFocused) {
            MobEspList.searchField.insert(chr.toString())
            return true
        }
        if (MobEspList.editing == null) return false
        MobEspList.nameField.insert(chr.toString())
        return true
    }
}
