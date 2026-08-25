package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor as GuiGraphics

private const val BAR_W = 4
private const val BAR_INSET = 5
private const val MIN_BAR_H = 18

object Scrollbar {

    fun draw(ctx: GuiGraphics, gui: ClickGUI, total: Int, top: Int = gui.contentY, height: Int = gui.contentH) {
        if (total <= height) return
        val barH = ((height.toFloat() / total) * height).toInt().coerceAtLeast(MIN_BAR_H)
        val progress = (-gui.scrollOffset / (total - height).toFloat()).coerceIn(0f, 1f)
        val barY = top + ((height - barH) * progress).toInt()
        val x = gui.panelX + PANEL_W - BAR_INSET - BAR_W
        GobbyTextures.capsule(ctx, x, top, BAR_W, height, cTrack)
        GobbyTextures.capsule(ctx, x, barY, BAR_W, barH, cInkGhost)
    }
}
