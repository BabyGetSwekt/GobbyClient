package gobby.gui.click

import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor as GuiGraphics

private const val SCROLL_STEP = 26f
private const val SECTION_TEXT_PAD = 3

object ModuleSettingsComponent {

    fun settingHeight(s: Setting<*>): Int = SettingsLayout.rowHeight(s)

    fun visibleSettings(mod: Module): List<Setting<*>> =
        mod.allSettings().filter { it.isVisible && it.parentDropdown == null }

    fun columnX(gui: ClickGUI): Int = SettingsLayout.contentLeft(gui.frame)

    fun totalContentHeight(mod: Module, gui: ClickGUI): Int =
        SettingsLayout.contentHeight(gui.frame, SettingsLayout.build(gui.frame, mod))

    fun draw(ctx: GuiGraphics, gui: ClickGUI, mod: Module, mx: Int, my: Int) {
        SettingsHeader.draw(ctx, gui, mod, mx, my)

        val top = SettingsLayout.contentTop(gui.frame)
        val bottom = SettingsLayout.contentBottom(gui.frame)
        val blocks = SettingsLayout.build(gui.frame, mod)
        gui.clampScroll(SettingsLayout.contentHeight(gui.frame, blocks), bottom - top)
        val shift = gui.scrollOffset.toInt()

        ctx.enableScissor(gui.panelX + SIDEBAR_W_SETTINGS, top, gui.panelX + PANEL_W, bottom)
        blocks.forEach { block -> drawBlock(ctx, gui, block, shift, mx, my) }
        ctx.disableScissor()

        Scrollbar.draw(ctx, gui, SettingsLayout.contentHeight(gui.frame, blocks), top, bottom - top)
        drawExpandedPicker(ctx, gui, blocks, shift)
        SelectorPopup.sync(gui)
        revealedSelectorRow(blocks)?.let { SelectorPopup.draw(ctx, gui, it.copy(y = it.y + shift), mx, my) }
    }

    private fun drawBlock(ctx: GuiGraphics, gui: ClickGUI, block: PlacedBlock, shift: Int, mx: Int, my: Int) {
        val sectionH = (tr.lineHeight * SETTINGS_SECTION_SCALE).toInt()
        drawTextScaled(
            ctx, block.x + SECTION_TEXT_PAD, block.y + shift + (SETTINGS_SECTION_H - sectionH) / 2,
            block.title, SETTINGS_SECTION_SCALE, cInkGhost, false
        )

        GobbyDraw.roundedBox(ctx, block.x, block.cardY + shift, block.w, block.cardH, SETTINGS_CARD_RADIUS, cCard, cCardEdge)

        block.rows.forEach { row ->
            val shifted = row.copy(y = row.y + shift)
            val clickable = row.setting !is ModelPreviewSetting
            val hovered = clickable && (mx to my) in shifted.rect
            CursorStyle.requestHandIf(hovered)
            SettingsControls.draw(ctx, gui, shifted, hovered, mx, my)
            if (hovered && row.setting.description.isNotEmpty()) {
                gui.tooltipText = row.setting.description
                gui.tooltipX = shifted.x + shifted.w + 6
                gui.tooltipY = shifted.y
            }
        }
    }

    private fun drawExpandedPicker(ctx: GuiGraphics, gui: ClickGUI, blocks: List<PlacedBlock>, shift: Int) {
        val row = expandedColorRow(blocks) ?: return
        ColorPickerPopup.draw(ctx, gui, row.copy(y = row.y + shift))
    }

    internal fun handleHexDrag(gui: ClickGUI, mod: Module, mx: Int): Boolean {
        val shift = gui.scrollOffset.toInt()
        val row = SettingsLayout.build(gui.frame, mod).flatMap { it.rows }
            .firstOrNull { (it.setting as? ColorSetting)?.expanded == true } ?: return false
        gui.hexField.placeCaret(ColorPickerPopup.caretIndexAt(gui, row.copy(y = row.y + shift), mx), extend = true)
        return true
    }

    private fun previewRowAt(gui: ClickGUI, mod: Module, mx: Int, my: Int): PlacedRow? {
        val shift = gui.scrollOffset.toInt()
        return SettingsLayout.build(gui.frame, mod).flatMap { it.rows }
            .map { it.copy(y = it.y + shift) }
            .firstOrNull { it.setting is ModelPreviewSetting && (mx to my) in it.rect }
    }

    private fun openSelectorRow(gui: ClickGUI, blocks: List<PlacedBlock>): PlacedRow? =
        gui.openSelector?.let { open -> blocks.flatMap { it.rows }.firstOrNull { it.setting === open } }

    private fun revealedSelectorRow(blocks: List<PlacedBlock>): PlacedRow? =
        SelectorPopup.visible()?.let { open -> blocks.flatMap { it.rows }.firstOrNull { it.setting === open } }

    private fun expandedColorRow(blocks: List<PlacedBlock>): PlacedRow? =
        blocks.flatMap { it.rows }.firstOrNull { (it.setting as? ColorSetting)?.expanded == true }

    fun handleClick(gui: ClickGUI, mod: Module, mx: Int, my: Int, button: Int): Boolean {
        val shift = gui.scrollOffset.toInt()
        val blocks = SettingsLayout.build(gui.frame, mod)

        openSelectorRow(gui, blocks)?.let { row ->
            if (SelectorPopup.handleClick(gui, row.copy(y = row.y + shift), mx, my)) return true
        }

        expandedColorRow(blocks)?.let { row ->
            val shifted = row.copy(y = row.y + shift)
            if (ColorPickerPopup.handleClick(gui, shifted, mx, my)) return true
        }

        if ((mx to my) in SettingsHeader.backRect(gui)) {
            gui.closeSettings()
            return true
        }

        if (my < SettingsLayout.contentTop(gui.frame)) return false

        previewRowAt(gui, mod, mx, my)?.let { row ->
            val preview = row.setting as ModelPreviewSetting
            if ((mx to my) in SettingsPreview.resetRect(row.rect)) preview.resetView() else gui.draggingPreview = preview
            return true
        }

        val row = blocks.flatMap { it.rows }.map { it.copy(y = it.y + shift) }
            .firstOrNull { (mx to my) in it.rect } ?: return false
        return InputHandler.dispatchSettingClick(gui, row, mx, my, button)
    }

    fun handleScroll(gui: ClickGUI, mod: Module, mx: Int, my: Int, vAmt: Double): Boolean {
        previewRowAt(gui, mod, mx, my)?.let { row ->
            (row.setting as ModelPreviewSetting).zoomBy(vAmt)
            return true
        }
        if (mx !in (gui.panelX + SIDEBAR_W_SETTINGS)..(gui.panelX + PANEL_W)) return false
        if (my !in SettingsLayout.contentTop(gui.frame)..SettingsLayout.contentBottom(gui.frame)) return false
        val viewport = SettingsLayout.contentBottom(gui.frame) - SettingsLayout.contentTop(gui.frame)
        gui.scrollTarget = ScrollBounds.clamp(
            gui.scrollTarget + vAmt.toFloat() * SCROLL_STEP, totalContentHeight(mod, gui), viewport
        )
        return true
    }
}
