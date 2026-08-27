package gobby.gui.screen.mobesp

import gobby.features.render.MobEsp
import gobby.gui.click.*

import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW

private const val ROW_RADIUS = 5
private const val ADD_RADIUS = 5
private const val SCROLL_STEP = 26f
private const val EMPTY_TOP = 18
private const val EMPTY_LABEL = "No mobs yet. Add one to start highlighting."
private const val HINT = "equals matches the whole name, contains matches part of it"
private const val TRASH_ICON = 10

internal object MobEspView : SearchableView() {

    override fun onOpened() = MobEspList.load()

    override fun onClosed() = MobEspList.close()

    override val searchField get() = MobEspList.searchField

    override val searchFocused get() = MobEspList.searchFocused

    override fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        SettingsHeader.draw(ctx, gui, MobEsp.category.iconTexture, MobEsp.name, HINT, mx, my, SettingsHeader.cancelIcon())
        followSearch(gui)
        gui.clampScroll(MobEspLayout.totalHeight(MobEspList.visibleRows().size), gui.contentH)

        val bottom = gui.contentY + gui.contentH
        ctx.enableScissor(gui.contentX, gui.contentY, gui.contentX + gui.contentW, bottom)
        drawAdd(ctx, MobEspLayout.addRect(gui), "Add mob", mx, my)
        MobEspList.visibleRows().forEachIndexed { index, row ->
            val r = MobEspLayout.rowRect(gui, index)
            if (r.y + r.h >= gui.contentY && r.y <= bottom) drawRow(ctx, row, r, mx, my)
        }
        ctx.disableScissor()

        drawSearch(ctx, MobEspLayout.searchRect(gui), mx, my)
        if (MobEspList.visibleRows().isEmpty()) drawEmpty(ctx, gui)
        Scrollbar.draw(ctx, gui, MobEspLayout.totalHeight(MobEspList.visibleRows().size))
        expandedRow(gui)?.let { (row, r) ->
            ColorPickerPopup.draw(ctx, gui, PlacedRow(row.color, r.x, r.y, r.w, r.h))
        }
    }

    private fun drawRow(ctx: GuiGraphicsExtractor, row: MobRow, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, ROW_RADIUS, if (hovered) cIconTile else cCard, cCardEdge)
        drawCheck(ctx, row, MobEspLayout.checkRect(r), mx, my)
        drawName(ctx, row, MobEspLayout.nameRect(r), mx, my)
        drawFilter(ctx, row, MobEspLayout.filterRect(r), mx, my)
        drawSwatch(ctx, row, MobEspLayout.swatchRect(r), mx, my)
        drawRemove(ctx, MobEspLayout.removeRect(r), mx, my)
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
            ?.let { it.value to MobEspLayout.rowRect(gui, it.index) }

    override fun handleClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean {
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
        if ((mx to my) in MobEspLayout.searchRect(gui)) {
            MobEspList.focusSearch()
            placeSearchCaret(MobEspLayout.searchRect(gui), mx)
            return true
        }
        MobEspList.blurSearch()
        blurNameUnlessClicked(gui, mx, my)
        if ((mx to my) in MobEspLayout.addRect(gui)) {
            MobEspList.stopEditing()
            MobEspList.add()
            return true
        }
        return clickRow(gui, mx, my)
    }


    private fun blurNameUnlessClicked(gui: ClickGUI, mx: Int, my: Int) {
        val editing = MobEspList.editing ?: return
        val index = MobEspList.visibleRows().indexOf(editing)
        if (index >= 0 && (mx to my) in MobEspLayout.nameRect(MobEspLayout.rowRect(gui, index))) return
        MobEspList.stopEditing()
    }

    private fun clickRow(gui: ClickGUI, mx: Int, my: Int): Boolean {
        val hit = MobEspList.visibleRows().withIndex().firstOrNull { (mx to my) in MobEspLayout.rowRect(gui, it.index) } ?: return false
        val row = hit.value
        val r = MobEspLayout.rowRect(gui, hit.index)
        when {
            (mx to my) in MobEspLayout.removeRect(r) -> MobEspList.remove(row)
            (mx to my) in MobEspLayout.swatchRect(r) -> {
                MobEspList.stopEditing()
                row.color.expanded = !row.color.expanded
            }
            (mx to my) in MobEspLayout.filterRect(r) -> MobEspList.cycleFilter(row)
            (mx to my) in MobEspLayout.checkRect(r) -> MobEspList.toggle(row)
            else -> startNameEdit(row, MobEspLayout.nameRect(r), mx)
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
            gui.scrollTarget + amount.toFloat() * SCROLL_STEP, MobEspLayout.totalHeight(MobEspList.visibleRows().size), gui.contentH
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
