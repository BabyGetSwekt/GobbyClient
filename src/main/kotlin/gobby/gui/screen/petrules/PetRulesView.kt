package gobby.gui.screen.petrules

import gobby.features.petrules.PetRule
import gobby.features.petrules.PetRules
import gobby.features.skyblock.PetsKeybind
import gobby.gui.click.*
import gobby.utils.render.CursorStyle
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier as ResourceLocation
import org.lwjgl.glfw.GLFW

private const val ROW_RADIUS = 5
private const val TRASH_ICON = 10
private const val EMPTY_TOP = 18
private const val SCROLL_STEP = 26f
private const val POPUP_RADIUS = 6
private const val POPUP_HEAD_PAD = 8
private const val TEXT_PAD = 6
private const val HEAD_PAD = 3

internal object PetRulesView : SearchableView() {

    override val searchField get() = PetRulesList.searchField

    override val searchFocused get() = PetRulesList.searchFocused

    override fun onClosed() = PetRulesList.close()

    private fun totalHeight() = PetRulesLayout.totalHeight(PetRulesList.visibleRules().size)

    override fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        SettingsHeader.draw(ctx, gui, PetsKeybind.category.iconTexture, "Pet Rules", "Swap to a pet automatically when something happens", mx, my, SettingsHeader.cancelIcon())
        followSearch(gui)
        gui.clampScroll(totalHeight(), gui.contentH)

        val top = PetRulesLayout.listTop(gui)
        val bottom = gui.contentY + gui.contentH
        ctx.enableScissor(gui.contentX, top, gui.contentX + gui.contentW, bottom)
        PetRulesList.visibleRules().forEachIndexed { index, rule ->
            val r = PetRulesLayout.rowRect(gui, index)
            if (r.y + r.h >= top && r.y <= bottom) drawRow(ctx, rule, r, mx, my)
        }
        ctx.disableScissor()

        drawAdd(ctx, PetRulesLayout.addRect(gui), "Add rule", mx, my)
        drawSearch(ctx, PetRulesLayout.searchRect(gui), mx, my)
        if (PetRulesList.visibleRules().isEmpty()) drawEmpty(ctx, gui)
        Scrollbar.draw(ctx, gui, totalHeight())
        if (PetRulesList.step != PickerStep.CLOSED) drawPicker(ctx, gui, mx, my)
    }

    private fun drawRow(ctx: GuiGraphicsExtractor, rule: PetRule, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, ROW_RADIUS, if (hovered) cIconTile else cCard, cCardEdge)
        drawCheck(ctx, rule, PetRulesLayout.checkRect(r), mx, my)
        drawLabel(ctx, PetRulesLayout.whenRect(r), "When: ${PetRules.labelOf(rule)}", if (rule.enabled) cInk else cInkFaint)
        drawLabel(ctx, PetRulesLayout.petRect(r), PetRules.petFor(rule)?.label ?: "Pet not found", if (rule.enabled) cInkSoft else cInkFaint)
        drawTrash(ctx, PetRulesLayout.trashRect(r), mx, my)
    }

    private fun drawCheck(ctx: GuiGraphicsExtractor, rule: PetRule, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        val tint = if (rule.enabled) cViolet else if (hovered) cInkSoft else cInkFaint
        GobbyTextures.checkbox(ctx, r.x, r.y, r.w, rule.enabled, tint)
    }

    private fun drawLabel(ctx: GuiGraphicsExtractor, r: Rect, text: String, color: Int) {
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        ctx.enableScissor(r.x, r.y, r.x + r.w, r.y + r.h)
        drawTextScaled(ctx, r.x, r.y + (r.h - h) / 2, text, SETTINGS_VALUE_SCALE, color, false)
        ctx.disableScissor()
    }

    private fun drawTrash(ctx: GuiGraphicsExtractor, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, ROW_RADIUS, if (hovered) cInvalid else cValueBox)
        GobbyTextures.trash(ctx, r.x + (r.w - TRASH_ICON) / 2, r.y + (r.h - TRASH_ICON) / 2, TRASH_ICON, if (hovered) cInk else cInkSoft)
    }

    private fun drawEmpty(ctx: GuiGraphicsExtractor, gui: ClickGUI) {
        val text = if (PetRules.rules.isEmpty()) "No rules yet. Add one to swap pets automatically." else "No rules matched your search."
        val w = textWScaled(text, SETTINGS_VALUE_SCALE)
        drawTextScaled(ctx, gui.contentX + (gui.contentW - w) / 2, PetRulesLayout.listTop(gui) + EMPTY_TOP, text, SETTINGS_VALUE_SCALE, cInkGhost, false)
    }

    private fun drawPicker(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val rows = PetRulesList.pickerRows()
        val popup = PetRulesLayout.popupRect(gui, rows.size)
        GobbyDraw.roundedBox(ctx, popup.x, popup.y, popup.w, popup.h, POPUP_RADIUS, cShellBg, cShellEdge)
        val headH = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, popup.x + POPUP_HEAD_PAD, popup.y + POPUP_HEAD_PAD, PetRulesList.pickerTitle(), SETTINGS_VALUE_SCALE, cInk, false)
        drawPickerClose(ctx, PetRulesLayout.popupCloseRect(popup), mx, my)
        val visible = PetRulesLayout.popupVisibleRows(popup)
        val offset = PetRulesList.pickerScroll
        rows.drop(offset).take(visible).forEachIndexed { index, label ->
            val absolute = index + offset
            drawPickerRow(ctx, PetRulesLayout.popupRowRect(popup, absolute, offset), label, PetRulesList.pickerIcon(absolute), mx, my)
        }
        if (rows.isEmpty()) drawTextScaled(ctx, popup.x + POPUP_HEAD_PAD, popup.y + headH + POPUP_HEAD_PAD * 2, "Nothing to choose", SETTINGS_VALUE_SCALE, cInkGhost, false)
        PetRulesLayout.popupBarRect(popup, rows.size, offset)?.let {
            GobbyDraw.roundedRect(ctx, it.x, it.y, it.w, it.h, it.w / 2, cInkFaint)
        }
    }

    private fun drawPickerRow(ctx: GuiGraphicsExtractor, r: Rect, label: String, icon: ResourceLocation?, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, ROW_RADIUS, if (hovered) cVioletSoft else cValueBox, if (hovered) cViolet else cCardEdge)
        val head = r.h - HEAD_PAD * 2
        icon?.let { ctx.blit(RenderPipelines.GUI_TEXTURED, it, r.x + HEAD_PAD, r.y + HEAD_PAD, 0f, 0f, head, head, head, head, -1) }
        val left = r.x + TEXT_PAD + if (icon == null) 0 else head + HEAD_PAD
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        ctx.enableScissor(left, r.y, r.x + r.w - TEXT_PAD, r.y + r.h)
        drawTextScaled(ctx, left, r.y + (r.h - h) / 2, label, SETTINGS_VALUE_SCALE, cInk, false)
        ctx.disableScissor()
    }

    private fun drawPickerClose(ctx: GuiGraphicsExtractor, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, ROW_RADIUS, if (hovered) cInvalid else cValueBox)
        val w = textWScaled("x", SETTINGS_VALUE_SCALE)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, r.x + (r.w - w) / 2, r.y + (r.h - h) / 2, "x", SETTINGS_VALUE_SCALE, cInk, false)
    }

    override fun handleClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean {
        if (PetRulesList.step != PickerStep.CLOSED) return clickPicker(gui, mx, my)
        if ((mx to my) in SettingsHeader.backRect(gui)) {
            gui.dismissView()
            return true
        }
        if ((mx to my) in PetRulesLayout.searchRect(gui)) {
            PetRulesList.focusSearch()
            placeSearchCaret(PetRulesLayout.searchRect(gui), mx)
            return true
        }
        PetRulesList.blurSearch()
        if ((mx to my) in PetRulesLayout.addRect(gui)) {
            PetRulesList.startPicker()
            return true
        }
        return clickRow(gui, mx, my)
    }

    private fun clickPicker(gui: ClickGUI, mx: Int, my: Int): Boolean {
        val rows = PetRulesList.pickerRows()
        val popup = PetRulesLayout.popupRect(gui, rows.size)
        if ((mx to my) in PetRulesLayout.popupCloseRect(popup) || (mx to my) !in popup) {
            PetRulesList.closePicker()
            return true
        }
        val visible = PetRulesLayout.popupVisibleRows(popup)
        val offset = PetRulesList.pickerScroll
        rows.indices.drop(offset).take(visible).firstOrNull { (mx to my) in PetRulesLayout.popupRowRect(popup, it, offset) }
            ?.let { PetRulesList.choose(it) }
        return true
    }

    private fun clickRow(gui: ClickGUI, mx: Int, my: Int): Boolean {
        val hit = PetRulesList.visibleRules().withIndex().firstOrNull { (mx to my) in PetRulesLayout.rowRect(gui, it.index) } ?: return false
        val row = PetRulesLayout.rowRect(gui, hit.index)
        if ((mx to my) in PetRulesLayout.checkRect(row)) {
            PetRules.toggle(hit.value)
            return true
        }
        if ((mx to my) !in PetRulesLayout.trashRect(row)) return false
        PetRulesList.delete(hit.value)
        return true
    }

    override fun handleScroll(gui: ClickGUI, mx: Int, my: Int, amount: Double): Boolean {
        if (PetRulesList.step != PickerStep.CLOSED) {
            val rows = PetRulesList.pickerRows()
            val popup = PetRulesLayout.popupRect(gui, rows.size)
            PetRulesList.scrollPicker(if (amount > 0) -1 else 1, rows.size, PetRulesLayout.popupVisibleRows(popup))
            return true
        }
        if (mx !in gui.contentX..(gui.contentX + gui.contentW)) return false
        gui.scrollTarget = ScrollBounds.clamp(gui.scrollTarget + amount.toFloat() * SCROLL_STEP, totalHeight(), gui.contentH)
        return true
    }

    override fun handleKey(gui: ClickGUI, key: Int): Boolean {
        if (PetRulesList.step != PickerStep.CLOSED) {
            if (key == GLFW.GLFW_KEY_ESCAPE) PetRulesList.closePicker()
            return true
        }
        if (PetRulesList.searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                PetRulesList.blurSearch()
                return true
            }
            return TextFieldKeys.handle(PetRulesList.searchField, key)
        }
        if (key != GLFW.GLFW_KEY_ESCAPE) return false
        gui.dismissView()
        return true
    }

    override fun handleChar(chr: Char): Boolean {
        if (!PetRulesList.searchFocused || PetRulesList.step != PickerStep.CLOSED) return false
        PetRulesList.searchField.insert(chr.toString())
        return true
    }
}
