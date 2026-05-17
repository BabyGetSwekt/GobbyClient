package gobby.gui.click

import net.minecraft.client.gui.GuiGraphics

object Scrollbar {

    fun draw(ctx: GuiGraphics, gui: ClickGUI, total: Int) {
        if (total <= gui.contentH) return
        val sbH = gui.contentH
        val barH = ((sbH.toFloat() / total) * sbH).toInt().coerceAtLeast(20)
        val maxOffset = (total - gui.contentH).toFloat()
        val progress = (-gui.scrollOffset / maxOffset).coerceIn(0f, 1f)
        val barY = gui.contentY + ((sbH - barH) * progress).toInt()
        fill(ctx, gui.panelX + PANEL_W - 6, barY, 3, barH, cScrollbar)
    }
}
