package gobby.gui.screen.modhider

import gobby.features.skyblock.ModIdHiderModule
import gobby.gui.click.*
import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW

private const val ROW_H = 22
private const val ROW_GAP = 4
private const val ROW_RADIUS = 5
private const val ROW_PAD = 9
private const val COL_GAP = 8
private const val TRASH_W = 14
private const val TRASH_ICON = 10
private const val BAR_H = 20
private const val BAR_RADIUS = 5
private const val ADD_SHARE = 3
private const val BAR_PARTS = 4
private const val SCROLL_STEP = 26f
private const val SCROLL_TAIL = 6
private const val EMPTY_TOP = 18
private const val NAME_MIN_W = 40
private const val EMPTY_LABEL = "No hidden mods yet."
private const val PLACEHOLDER = "Type a mod ID"
private const val RESTART_HINT = "Hidden mods apply after a game restart"
private const val LOCK_ICON = 10

internal object ModIdView : SearchableView() {

    override val searchField get() = ModIdList.searchField

    override val searchFocused get() = ModIdList.searchFocused

    override fun onOpened() = ModIdList.load()

    override fun onClosed() = ModIdList.close()

    private fun addRect(gui: ClickGUI) =
        Rect(gui.contentX, gui.contentY, (gui.contentW - COL_GAP) * ADD_SHARE / BAR_PARTS, BAR_H)

    private fun searchRect(gui: ClickGUI): Rect {
        val left = addRect(gui).let { it.x + it.w + COL_GAP }
        return Rect(left, gui.contentY, gui.contentX + gui.contentW - left, BAR_H)
    }

    private fun rowRect(gui: ClickGUI, index: Int): Rect {
        val top = gui.contentY + BAR_H + ROW_GAP + index * (ROW_H + ROW_GAP) + gui.scrollOffset.toInt()
        return Rect(gui.contentX, top, gui.contentW, ROW_H)
    }

    private fun totalHeight(): Int =
        BAR_H + ROW_GAP + ModIdList.visibleRows().size * (ROW_H + ROW_GAP) + SCROLL_TAIL

    private fun trashRect(r: Rect) =
        Rect(r.x + r.w - ROW_PAD - TRASH_W, r.y + (r.h - TRASH_W) / 2, TRASH_W, TRASH_W)

    private fun idRect(r: Rect) =
        Rect(r.x + ROW_PAD, r.y, (trashRect(r).x - COL_GAP - r.x - ROW_PAD).coerceAtLeast(NAME_MIN_W), r.h)

    override fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val subtitle = ModIdList.notice() ?: RESTART_HINT
        SettingsHeader.draw(ctx, gui, ModIdHiderModule.category.iconTexture, ModIdHiderModule.name, subtitle, mx, my, SettingsHeader.cancelIcon())
        followSearch(gui)
        gui.clampScroll(totalHeight(), gui.contentH)

        val bottom = gui.contentY + gui.contentH
        ctx.enableScissor(gui.contentX, gui.contentY, gui.contentX + gui.contentW, bottom)
        drawAdd(ctx, addRect(gui), "Add mod ID", mx, my)
        ModIdList.visibleRows().forEachIndexed { index, row ->
            val r = rowRect(gui, index)
            if (r.y + r.h >= gui.contentY && r.y <= bottom) drawRow(ctx, row, r, mx, my)
        }
        ctx.disableScissor()

        drawSearch(ctx, searchRect(gui), mx, my)
        if (ModIdList.visibleRows().isEmpty()) drawEmpty(ctx, gui)
        Scrollbar.draw(ctx, gui, totalHeight())
    }

    private fun drawRow(ctx: GuiGraphicsExtractor, row: ModIdRow, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, ROW_RADIUS, if (hovered) cIconTile else cCard, cCardEdge)
        drawId(ctx, row, idRect(r), mx, my)
        if (ModIdList.isProtected(row)) drawProtected(ctx, trashRect(r)) else drawTrash(ctx, trashRect(r), mx, my)
    }

    private fun drawId(ctx: GuiGraphicsExtractor, row: ModIdRow, r: Rect, mx: Int, my: Int) {
        val editing = ModIdList.editing === row
        CursorStyle.requestHandIf((mx to my) in r)
        val shown = ModIdList.idOf(row)
        ctx.enableScissor(r.x, r.y, r.x + r.w, r.y + r.h)
        TextFieldView.draw(
            ctx, ModIdList.idField, r.x, r.y, r.h, SETTINGS_VALUE_SCALE, cInk, editing,
            shown = shown, placeholder = if (editing) "" else PLACEHOLDER
        )
        ctx.disableScissor()
    }

    private fun drawProtected(ctx: GuiGraphicsExtractor, r: Rect) =
        GobbyTextures.lock(ctx, r.x + (r.w - LOCK_ICON) / 2, r.y + (r.h - LOCK_ICON) / 2, LOCK_ICON, cInkGhost)

    private fun drawTrash(ctx: GuiGraphicsExtractor, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, if (hovered) cInvalid else cValueBox)
        GobbyTextures.trash(
            ctx, r.x + (r.w - TRASH_ICON) / 2, r.y + (r.h - TRASH_ICON) / 2, TRASH_ICON,
            if (hovered) cInk else cInkSoft
        )
    }

    private fun drawEmpty(ctx: GuiGraphicsExtractor, gui: ClickGUI) {
        val w = textWScaled(EMPTY_LABEL, SETTINGS_VALUE_SCALE)
        drawTextScaled(
            ctx, gui.contentX + (gui.contentW - w) / 2, gui.contentY + BAR_H + EMPTY_TOP,
            EMPTY_LABEL, SETTINGS_VALUE_SCALE, cInkGhost, false
        )
    }

    override fun handleClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean {
        if ((mx to my) in SettingsHeader.backRect(gui)) {
            gui.dismissView()
            return true
        }
        if ((mx to my) in searchRect(gui)) {
            ModIdList.focusSearch()
            placeSearchCaret(searchRect(gui), mx)
            return true
        }
        ModIdList.blurSearch()
        blurIdUnlessClicked(gui, mx, my)
        if ((mx to my) in addRect(gui)) {
            ModIdList.add()
            return true
        }
        return clickRow(gui, mx, my)
    }

    private fun blurIdUnlessClicked(gui: ClickGUI, mx: Int, my: Int) {
        val editing = ModIdList.editing ?: return
        val index = ModIdList.visibleRows().indexOf(editing)
        if (index >= 0 && (mx to my) in idRect(rowRect(gui, index))) return
        ModIdList.stopEditing()
    }

    private fun clickRow(gui: ClickGUI, mx: Int, my: Int): Boolean {
        val hit = ModIdList.visibleRows().withIndex().firstOrNull { (mx to my) in rowRect(gui, it.index) } ?: return false
        val row = hit.value
        val r = rowRect(gui, hit.index)
        if ((mx to my) in trashRect(r)) {
            ModIdList.remove(row)
            return true
        }
        ModIdList.startEditing(row)
        val field = ModIdList.idField
        field.placeCaret(TextFieldView.caretIndexAt(field.text, idRect(r).x, mx, SETTINGS_VALUE_SCALE), extend = false)
        return true
    }

    override fun handleScroll(gui: ClickGUI, mx: Int, my: Int, amount: Double): Boolean {
        if (mx !in gui.contentX..(gui.contentX + gui.contentW)) return false
        gui.scrollTarget = ScrollBounds.clamp(
            gui.scrollTarget + amount.toFloat() * SCROLL_STEP, totalHeight(), gui.contentH
        )
        return true
    }

    override fun handleKey(gui: ClickGUI, key: Int): Boolean {
        if (ModIdList.searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                ModIdList.blurSearch()
                return true
            }
            return TextFieldKeys.handle(ModIdList.searchField, key)
        }
        if (ModIdList.editing == null) {
            if (key != GLFW.GLFW_KEY_ESCAPE) return false
            gui.dismissView()
            return true
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
            ModIdList.stopEditing()
            return true
        }
        return TextFieldKeys.handle(ModIdList.idField, key)
    }

    override fun handleChar(chr: Char): Boolean {
        if (ModIdList.searchFocused) {
            ModIdList.searchField.insert(chr.toString())
            return true
        }
        if (ModIdList.editing == null) return false
        ModIdList.idField.insert(chr.toString())
        return true
    }
}
