package gobby.gui.click

import net.minecraft.client.gui.GuiGraphics

object ModuleSettingsComponent {

    private const val DESC_SCALE = 0.75f
    private const val DESC_MAX_LINES = 6
    private const val DESC_LEFT_PAD = 8
    private const val DESC_LINE_GAP = 1
    private const val TITLE_DESC_GAP = 4
    private const val HEADER_TOP_PAD = 4
    private const val HEADER_BOTTOM_PAD = 8
    private const val TOGGLE_W = 26
    private const val TOGGLE_H = 12
    private const val TOGGLE_RIGHT_PAD = 14

    fun settingHeight(s: Setting<*>): Int {
        if (s is ColorSetting && s.expanded) return SH + COLOR_PICKER_H
        if (s is DropDownSetting && s.expanded) return SH + s.children.filter { it.isVisible }.sumOf { settingHeight(it) }
        return SH
    }

    fun visibleSettings(mod: Module): List<Setting<*>> =
        mod.allSettings().filter { it.isVisible && it.parentDropdown == null }

    private fun descLineHeight(): Int = TextWrap.scaledLineHeight(DESC_SCALE, DESC_LINE_GAP)

    private fun descMaxWidth(gui: ClickGUI): Int {
        val rightLimit = gui.panelX + (PANEL_W * 0.9f).toInt()
        return (rightLimit - (gui.contentX + DESC_LEFT_PAD)).coerceAtLeast(40)
    }

    private fun descLines(mod: Module, gui: ClickGUI): List<String> =
        if (mod.description.isEmpty()) emptyList()
        else TextWrap.wrap(mod.description, descMaxWidth(gui), DESC_SCALE, DESC_MAX_LINES)

    private fun headerBlockHeight(mod: Module, gui: ClickGUI): Int {
        val descH = descLines(mod, gui).size.let { if (it == 0) 0 else TITLE_DESC_GAP + it * descLineHeight() }
        return HEADER_TOP_PAD + tr.lineHeight + descH + HEADER_BOTTOM_PAD
    }

    fun totalContentHeight(mod: Module, gui: ClickGUI): Int =
        headerBlockHeight(mod, gui) + visibleSettings(mod).sumOf { settingHeight(it) + 2 }

    fun columnX(gui: ClickGUI): Int = gui.contentX

    fun draw(ctx: GuiGraphics, gui: ClickGUI, mod: Module, mx: Int, my: Int) {
        val clipTop = gui.contentY
        val clipBot = gui.contentY + gui.contentH
        ctx.enableScissor(gui.contentX - 4, clipTop, gui.panelX + PANEL_W - 4, clipBot)

        val baseY = gui.contentY + gui.scrollOffset.toInt()
        val headerH = headerBlockHeight(mod, gui)
        drawHeaderBlock(ctx, gui, mod, baseY, headerH)

        var y = baseY + headerH
        val px = columnX(gui)
        for (s in visibleSettings(mod)) {
            val h = settingHeight(s)
            if (y + h >= clipTop && y <= clipBot)
                SettingRenderer.drawSettingRow(ctx, gui, px, y, s, mx, my, clipTop, clipBot)
            y += h + 2
        }

        ctx.disableScissor()
        Scrollbar.draw(ctx, gui, totalContentHeight(mod, gui))
    }

    private fun toggleRect(gui: ClickGUI, baseY: Int) = Rect(
        gui.panelX + PANEL_W - TOGGLE_W - TOGGLE_RIGHT_PAD,
        baseY + HEADER_TOP_PAD + (tr.lineHeight - TOGGLE_H) / 2,
        TOGGLE_W, TOGGLE_H
    )

    private fun drawHeaderBlock(ctx: GuiGraphics, gui: ClickGUI, mod: Module, baseY: Int, headerH: Int) {
        val px = columnX(gui)
        fill(ctx, px, baseY + 4, 2, headerH - 8, cAccent)
        drawText(ctx, px + DESC_LEFT_PAD, baseY + HEADER_TOP_PAD, mod.name, cTextBright)

        descLines(mod, gui).forEachIndexed { idx, line ->
            val dy = baseY + HEADER_TOP_PAD + tr.lineHeight + TITLE_DESC_GAP + idx * descLineHeight()
            drawTextScaled(ctx, px + DESC_LEFT_PAD, dy, line, DESC_SCALE, cTextGray)
        }

        if (mod.hasToggle && mod.toggled && !mod.isAlwaysEnabled) {
            val t = toggleRect(gui, baseY)
            fill(ctx, t.x, t.y, t.w, t.h, if (mod.enabled) cToggleOn else cToggleOff)
            val knobX = if (mod.enabled) t.x + t.w - 9 else t.x + 1
            fill(ctx, knobX, t.y + 1, 8, t.h - 2, cKnob)
            val statusText = if (mod.enabled) "ON" else "OFF"
            val statusCol = if (mod.enabled) cAccent else cTextGray
            val sw = textWScaled(statusText, 0.75f)
            drawTextScaled(ctx, t.x - sw - 6, t.y + 2, statusText, 0.75f, statusCol)
        }
    }

    fun handleClick(gui: ClickGUI, mod: Module, mx: Int, my: Int, button: Int): Boolean {
        val baseY = gui.contentY + gui.scrollOffset.toInt()
        if (mod.hasToggle && mod.toggled && !mod.isAlwaysEnabled && (mx to my) in toggleRect(gui, baseY)) {
            mod.enabled = !mod.enabled
            return true
        }

        val px = columnX(gui)
        var y = baseY + headerBlockHeight(mod, gui)
        for (s in visibleSettings(mod)) {
            val h = settingHeight(s)
            if (mx in px..(px + PW) && my in y..(y + h))
                return InputHandler.dispatchSettingClick(gui, s, px, y, mx, my, button)
            if (s is DropDownSetting && s.expanded) {
                var cy = y + SH
                for (child in s.children.filter { it.isVisible }) {
                    val ch = settingHeight(child)
                    if (mx in px..(px + PW) && my in cy..(cy + ch))
                        return InputHandler.dispatchSettingClick(gui, child, px, cy, mx, my, button)
                    cy += ch + 2
                }
            }
            y += h + 2
        }
        return false
    }

    fun handleScroll(gui: ClickGUI, mod: Module, mx: Int, my: Int, vAmt: Double): Boolean {
        if (mx !in gui.contentX..(gui.panelX + PANEL_W)) return false
        if (my !in gui.contentY..(gui.contentY + gui.contentH)) return false
        val maxOffset = (totalContentHeight(mod, gui) - gui.contentH).coerceAtLeast(0).toFloat()
        gui.scrollTarget = (gui.scrollTarget + vAmt.toFloat() * 24f).coerceIn(-maxOffset, 0f)
        return true
    }

}
