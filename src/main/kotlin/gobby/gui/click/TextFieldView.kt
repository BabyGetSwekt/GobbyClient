package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor

private const val SELECTION_INSET = 3
private const val CARET_W = 1
private const val CARET_PAD = 2

internal object TextFieldView {

    fun draw(
        ctx: GuiGraphicsExtractor, field: TextField, x: Int, top: Int, height: Int,
        scale: Float, color: Int, active: Boolean, shown: String = field.text, placeholder: String = ""
    ) {
        val textH = (tr.lineHeight * scale).toInt()
        val textY = top + (height - textH) / 2
        if (shown.isEmpty() && placeholder.isNotEmpty()) {
            drawTextScaled(ctx, x, textY, placeholder, scale, cInkGhost, false)
        }
        if (active && field.hasSelection) {
            val from = x + textWScaled(shown.take(field.selectionStart), scale)
            val to = x + textWScaled(shown.take(field.selectionEnd), scale)
            ctx.fill(from, top + SELECTION_INSET, to, top + height - SELECTION_INSET, cSelection)
        }
        drawTextScaled(ctx, x, textY, shown, scale, color, false)
        if (!active || !field.caretVisible()) return
        val caretX = x + textWScaled(shown.take(field.caret), scale)
        val caretTop = (textY - CARET_PAD).coerceAtLeast(top + SELECTION_INSET)
        val caretBottom = (textY + textH + CARET_PAD).coerceAtMost(top + height - SELECTION_INSET)
        ctx.fill(caretX, caretTop, caretX + CARET_W, caretBottom, cInk)
    }

    fun caretIndexAt(text: String, originX: Int, mouseX: Int, scale: Float): Int =
        (1..text.length).lastOrNull { originX + textWScaled(text.take(it), scale) <= mouseX } ?: 0
}
