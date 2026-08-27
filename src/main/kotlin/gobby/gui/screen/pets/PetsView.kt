package gobby.gui.screen.pets

import gobby.features.skyblock.PetsKeybind
import gobby.utils.managers.PetEntry
import gobby.utils.managers.PETS_FOLDER
import gobby.utils.managers.PetManager
import gobby.utils.render.FaceTextures
import gobby.gui.click.*
import gobby.gui.screen.petrules.openPetRules
import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import org.lwjgl.glfw.GLFW

private const val ROW_RADIUS = 5
private const val RESET_ICON = 10
private const val BAR_RADIUS = 5
private const val REFRESH_ICON = 10
private const val RULES_ICON = 13
private const val CHECK_SIZE = 12
private const val SCROLL_STEP = 26f
private const val EMPTY_TOP = 18

internal object PetsView : SearchableView() {

    override val searchField get() = PetsList.searchField

    override val searchFocused get() = PetsList.searchFocused

    override fun onOpened() = PetsList.open()

    override fun onClosed() = PetsList.close()

    private fun totalHeight(): Int = PetsLayout.totalHeight(PetsList.visiblePets().size)

    override fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        SettingsHeader.draw(ctx, gui, PetsKeybind.category.iconTexture, PetsKeybind.name, "Press a pet's key to swap, in the Pets menu or anywhere", mx, my, SettingsHeader.cancelIcon())
        followSearch(gui)
        gui.clampScroll(totalHeight(), gui.contentH)

        val top = PetsLayout.listTop(gui)
        val bottom = gui.contentY + gui.contentH
        ctx.enableScissor(gui.contentX, top, gui.contentX + gui.contentW, bottom)
        PetsList.visiblePets().forEachIndexed { index, pet ->
            val r = PetsLayout.rowRect(gui, index)
            if (r.y + r.h >= top && r.y <= bottom) drawRow(ctx, pet, r, mx, my)
        }
        ctx.disableScissor()

        drawRefresh(ctx, gui, mx, my)
        drawToggle(ctx, PetsLayout.toggleRect(gui, 0), "Prevent Unequip", PetManager.preventUnequip, mx, my)
        drawToggle(ctx, PetsLayout.toggleRect(gui, 1), "Close If Equipped", PetManager.closeIfEquipped, mx, my)
        drawToggle(ctx, PetsLayout.toggleRect(gui, 2), "Swapping outside of Pets menu", PetManager.swapOutsideMenu, mx, my)
        drawSearch(ctx, PetsLayout.searchRect(gui), mx, my)
        drawRules(ctx, PetsLayout.rulesRect(gui), mx, my)
        if (PetsList.visiblePets().isEmpty()) drawEmpty(ctx, gui)
        Scrollbar.draw(ctx, gui, totalHeight())
    }

    private fun drawRefresh(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val r = PetsLayout.refreshRect(gui)
        val busy = PetsList.scanning
        val hovered = !busy && (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, if (hovered) cViolet else cValueBox)
        val tint = if (busy) cInkGhost else cInk
        val labelW = textWScaled("Refresh", SETTINGS_VALUE_SCALE)
        val iconX = r.x + (r.w - labelW - REFRESH_ICON - COL_GAP / 2) / 2
        GobbyTextures.reset(ctx, iconX, r.y + (r.h - REFRESH_ICON) / 2, REFRESH_ICON, tint)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, iconX + REFRESH_ICON + COL_GAP / 2, r.y + (r.h - h) / 2, "Refresh", SETTINGS_VALUE_SCALE, tint, false)
    }

    private fun drawRules(ctx: GuiGraphicsExtractor, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, if (hovered) cViolet else cValueBox)
        GobbyTextures.rules(ctx, r.x + (r.w - RULES_ICON) / 2, r.y + (r.h - RULES_ICON) / 2, RULES_ICON, -1)
    }

    private fun drawToggle(ctx: GuiGraphicsExtractor, r: Rect, label: String, on: Boolean, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, cValueBox, if (hovered) cViolet else cCardEdge)
        val boxX = r.x + SEARCH_PAD
        GobbyTextures.checkbox(ctx, boxX, r.y + (r.h - CHECK_SIZE) / 2, CHECK_SIZE, on, if (on) cViolet else cInkFaint)
        val textX = boxX + CHECK_SIZE + COL_GAP / 2
        val h = (tr.lineHeight * SETTINGS_SUBTITLE_SCALE).toInt()
        ctx.enableScissor(textX, r.y, r.x + r.w - SEARCH_PAD / 2, r.y + r.h)
        drawTextScaled(ctx, textX, r.y + (r.h - h) / 2, label, SETTINGS_SUBTITLE_SCALE, if (on) cInk else cInkSoft, false)
        ctx.disableScissor()
    }

    private fun drawRow(ctx: GuiGraphicsExtractor, pet: PetEntry, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, ROW_RADIUS, if (hovered) cIconTile else cCard, cCardEdge)
        drawHead(ctx, pet, PetsLayout.headRect(r))
        drawName(ctx, pet, PetsLayout.nameRect(r))
        drawKey(ctx, pet, PetsLayout.keyRect(r), mx, my)
        drawReset(ctx, PetsLayout.resetRect(r), mx, my)
        drawEquip(ctx, PetsLayout.equipRect(r), mx, my)
    }

    private fun drawHead(ctx: GuiGraphicsExtractor, pet: PetEntry, r: Rect) {
        val icon = FaceTextures.textureFor(PETS_FOLDER, pet.uuid)
        if (icon == null) {
            GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, cIconTile)
            return
        }
        ctx.blit(RenderPipelines.GUI_TEXTURED, icon, r.x, r.y, 0f, 0f, r.w, r.h, r.w, r.h, -1)
    }

    private fun drawName(ctx: GuiGraphicsExtractor, pet: PetEntry, r: Rect) {
        val text = (if (pet.favorite) "★ " else "") + pet.label + if (pet.hasSkin) " ✦" else ""
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        ctx.enableScissor(r.x, r.y, r.x + r.w, r.y + r.h)
        drawTextScaled(ctx, r.x, r.y + (r.h - h) / 2, text, SETTINGS_VALUE_SCALE, cInk, false)
        ctx.disableScissor()
    }

    private fun drawKey(ctx: GuiGraphicsExtractor, pet: PetEntry, r: Rect, mx: Int, my: Int) {
        val listening = PetsList.listening === pet
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        val fill = if (listening) cVioletSoft else cValueBox
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, fill, if (listening || hovered) cViolet else fill)
        val text = if (listening) "..." else KeyNames.of(PetManager.keyOf(pet.uuid))
        val w = textWScaled(text, SETTINGS_VALUE_SCALE)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, r.x + (r.w - w) / 2, r.y + (r.h - h) / 2, text, SETTINGS_VALUE_SCALE, cInkSoft, false)
    }

    private fun drawReset(ctx: GuiGraphicsExtractor, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, if (hovered) cSidebarActive else cValueBox)
        GobbyTextures.reset(ctx, r.x + (r.w - RESET_ICON) / 2, r.y + (r.h - RESET_ICON) / 2, RESET_ICON, if (hovered) cInk else cInkSoft)
    }

    private fun drawEquip(ctx: GuiGraphicsExtractor, r: Rect, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, if (hovered) cVioletSoft else cValueBox, if (hovered) cViolet else cCardEdge)
        val w = textWScaled("Equip", SETTINGS_VALUE_SCALE)
        val h = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, r.x + (r.w - w) / 2, r.y + (r.h - h) / 2, "Equip", SETTINGS_VALUE_SCALE, if (hovered) cInk else cInkSoft, false)
    }

    private fun drawEmpty(ctx: GuiGraphicsExtractor, gui: ClickGUI) {
        val text = when {
            PetsList.scanning -> "Reading your pets menu..."
            !PetsList.scanned -> "Never scanned. Press Refresh on Skyblock."
            PetManager.pets.isEmpty() -> "No pets found."
            else -> "No pets matched your search."
        }
        val w = textWScaled(text, SETTINGS_VALUE_SCALE)
        drawTextScaled(ctx, gui.contentX + (gui.contentW - w) / 2, PetsLayout.listTop(gui) + EMPTY_TOP, text, SETTINGS_VALUE_SCALE, cInkGhost, false)
    }

    override fun handleClick(gui: ClickGUI, mx: Int, my: Int, button: Int): Boolean {
        if ((mx to my) in SettingsHeader.backRect(gui)) {
            gui.dismissView()
            return true
        }
        if ((mx to my) in PetsLayout.searchRect(gui)) {
            PetsList.focusSearch()
            placeSearchCaret(PetsLayout.searchRect(gui), mx)
            return true
        }
        PetsList.listening?.let { return PetsList.bindMouse(button) }
        PetsList.blurSearch()
        if ((mx to my) in PetsLayout.rulesRect(gui)) {
            openPetRules()
            return true
        }
        if ((mx to my) in PetsLayout.refreshRect(gui)) {
            if (!PetsList.scanning) PetsList.refresh()
            return true
        }
        if ((mx to my) in PetsLayout.toggleRect(gui, 0)) {
            PetManager.preventUnequip = !PetManager.preventUnequip
            return true
        }
        if ((mx to my) in PetsLayout.toggleRect(gui, 1)) {
            PetManager.closeIfEquipped = !PetManager.closeIfEquipped
            return true
        }
        if ((mx to my) in PetsLayout.toggleRect(gui, 2)) {
            PetManager.swapOutsideMenu = !PetManager.swapOutsideMenu
            return true
        }
        return clickRow(gui, mx, my)
    }

    private fun clickRow(gui: ClickGUI, mx: Int, my: Int): Boolean {
        val hit = PetsList.visiblePets().withIndex().firstOrNull { (mx to my) in PetsLayout.rowRect(gui, it.index) } ?: return false
        val r = PetsLayout.rowRect(gui, hit.index)
        when {
            (mx to my) in PetsLayout.equipRect(r) -> PetsList.equip(hit.value)
            (mx to my) in PetsLayout.resetRect(r) -> PetsList.clearKey(hit.value)
            (mx to my) in PetsLayout.keyRect(r) -> PetsList.listenOn(hit.value)
            else -> return false
        }
        return true
    }

    override fun handleScroll(gui: ClickGUI, mx: Int, my: Int, amount: Double): Boolean {
        if (mx !in gui.contentX..(gui.contentX + gui.contentW)) return false
        gui.scrollTarget = ScrollBounds.clamp(gui.scrollTarget + amount.toFloat() * SCROLL_STEP, totalHeight(), gui.contentH)
        return true
    }

    override fun handleKey(gui: ClickGUI, key: Int): Boolean {
        if (PetsList.listening != null) {
            PetsList.bind(if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE) 0 else key)
            return true
        }
        if (PetsList.searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                PetsList.blurSearch()
                return true
            }
            return TextFieldKeys.handle(PetsList.searchField, key)
        }
        if (key != GLFW.GLFW_KEY_ESCAPE) return false
        gui.dismissView()
        return true
    }

    override fun handleChar(chr: Char): Boolean {
        if (!PetsList.searchFocused) return false
        PetsList.searchField.insert(chr.toString())
        return true
    }
}
