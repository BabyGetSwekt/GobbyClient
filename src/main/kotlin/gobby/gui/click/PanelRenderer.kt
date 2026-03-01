package gobby.gui.click

import gobby.gui.click.ClickGUITheme.BOTTOM_ACCENT
import gobby.gui.click.ClickGUITheme.HH
import gobby.gui.click.ClickGUITheme.MH
import gobby.gui.click.ClickGUITheme.PAD
import gobby.gui.click.ClickGUITheme.PW
import gobby.gui.click.ClickGUITheme.SCROLLBAR_W
import gobby.gui.click.ClickGUITheme.cAccent
import gobby.gui.click.ClickGUITheme.cBorder
import gobby.gui.click.ClickGUITheme.cEnabled
import gobby.gui.click.ClickGUITheme.cEnabledHov
import gobby.gui.click.ClickGUITheme.cHeaderBg
import gobby.gui.click.ClickGUITheme.cHeaderLine
import gobby.gui.click.ClickGUITheme.cHover
import gobby.gui.click.ClickGUITheme.cPanelBg
import gobby.gui.click.ClickGUITheme.cScrollbar
import gobby.gui.click.ClickGUITheme.cSeparator
import gobby.gui.click.ClickGUITheme.cText
import gobby.gui.click.ClickGUITheme.cTextBright
import gobby.gui.click.ClickGUITheme.cTextDark
import gobby.gui.click.ClickGUITheme.cTextGray
import gobby.gui.click.ClickGUITheme.tr
import net.minecraft.client.gui.DrawContext

object PanelRenderer {

    fun drawPanel(ctx: DrawContext, gui: ClickGUI, panel: ClickGUI.PanelState, mx: Int, my: Int) {
        val px = panel.x.toInt()
        val py = panel.y.toInt()

        // Header background
        ClickGUITheme.fill(ctx, px, py, PW, HH, cHeaderBg)

        // Header bottom accent line
        ClickGUITheme.fill(ctx, px, py + HH - 1, PW, 1, cHeaderLine)

        // Header text centered
        val headerText = panel.category.displayName
        val nameW = ClickGUITheme.textW(headerText)
        ClickGUITheme.drawText(ctx, px + (PW - nameW) / 2, py + (HH - tr.fontHeight) / 2, headerText, cTextBright)

        // Collapse indicator
        val arrow = if (panel.collapsed) "+" else "-"
        ClickGUITheme.drawText(ctx, px + PW - PAD - ClickGUITheme.textW(arrow), py + (HH - tr.fontHeight) / 2, arrow, cTextGray)

        if (panel.collapsed) {
            ClickGUITheme.fill(ctx, px, py + HH, PW, BOTTOM_ACCENT, cAccent)
            return
        }

        val modules = gui.getModulesForPanel(panel)
        if (modules.isEmpty()) {
            ClickGUITheme.fill(ctx, px, py + HH, PW, BOTTOM_ACCENT, cAccent)
            return
        }

        var contentH = 0
        for (m in modules) {
            contentH += MH
            if (m.expanded) contentH += gui.settingsHeight(m)
        }

        val screenHeight = gui.height
        val maxVisH = (screenHeight - py - HH - 44).coerceAtLeast(30)
        val maxScroll = (contentH - maxVisH).coerceAtLeast(0).toFloat()
        panel.scrollOffset = panel.scrollOffset.coerceIn(0f, maxScroll)
        val visH = contentH.coerceAtMost(maxVisH)

        // Panel body background
        ClickGUITheme.fill(ctx, px, py + HH, PW, visH, cPanelBg)

        // Side borders
        ClickGUITheme.fill(ctx, px, py, 1, HH + visH + BOTTOM_ACCENT, cBorder)
        ClickGUITheme.fill(ctx, px + PW - 1, py, 1, HH + visH + BOTTOM_ACCENT, cBorder)

        // Scissor for content
        ctx.enableScissor(px, py + HH, px + PW, py + HH + visH)
        var y = py + HH - panel.scrollOffset.toInt()

        for (module in modules) {
            drawModuleRow(ctx, gui, px, y, module, mx, my, py + HH, py + HH + visH)
            y += MH

            if (module.expanded) {
                for (setting in module.allSettings()) {
                    if (!setting.isVisible) continue
                    SettingRenderer.drawSettingRow(ctx, gui, px, y, setting, mx, my, py + HH, py + HH + visH)
                    y += gui.settingHeight(setting)
                }
            }
        }
        ctx.disableScissor()

        // Bottom accent line
        ClickGUITheme.fill(ctx, px, py + HH + visH, PW, BOTTOM_ACCENT, cAccent)

        // Scrollbar
        if (maxScroll > 0) {
            val barH = ((visH.toFloat() / contentH) * visH).toInt().coerceAtLeast(14)
            val barY = py + HH + ((panel.scrollOffset / (maxScroll + visH)) * visH).toInt()
            ClickGUITheme.fill(ctx, px + PW - SCROLLBAR_W - 1, barY, SCROLLBAR_W, barH, cScrollbar)
        }
    }

    private fun drawModuleRow(ctx: DrawContext, gui: ClickGUI, px: Int, y: Int, module: Module, mx: Int, my: Int, clipTop: Int, clipBot: Int) {
        val inView = y + MH > clipTop && y < clipBot
        val hovered = inView && mx in px..(px + PW) && my in y.coerceAtLeast(clipTop)..(y + MH).coerceAtMost(clipBot)
        val enabled = module.toggled && module.enabled

        if (enabled) {
            ClickGUITheme.fill(ctx, px, y, PW, MH, if (hovered) cEnabledHov else cEnabled)
        } else if (hovered) {
            ClickGUITheme.fill(ctx, px, y, PW, MH, cHover)
        }

        ClickGUITheme.fill(ctx, px + 1, y + MH - 1, PW - 2, 1, cSeparator)

        // Module name - centered
        val nameW = ClickGUITheme.textW(module.name)
        val textCol = if (enabled) cTextBright else cText
        ClickGUITheme.drawText(ctx, px + (PW - nameW) / 2, y + (MH - tr.fontHeight) / 2, module.name, textCol)

        // Settings indicator dots on right when module has settings
        if (module.allSettings().any { it.isVisible }) {
            val dotCol = if (enabled) cTextBright else cTextDark
            val dotY = y + MH / 2
            ClickGUITheme.fill(ctx, px + PW - PAD - 1, dotY - 3, 2, 2, dotCol)
            ClickGUITheme.fill(ctx, px + PW - PAD - 1, dotY, 2, 2, dotCol)
            ClickGUITheme.fill(ctx, px + PW - PAD - 1, dotY + 3, 2, 2, dotCol)
        }

        // Tooltip
        if (hovered && module.description.isNotEmpty()) {
            gui.tooltipText = module.description
            gui.tooltipX = px + PW + 8
            gui.tooltipY = y
        }
    }
}
