package gobby.gui.click

import gobby.utils.render.Animation
import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW

private const val ICON = 13
private const val BAR_H = 20
private const val BAR_W = 138
private const val EXPAND_MS = 200L
private const val TRAIL_GAP = 10
private const val SIDE_PAD = 6
private const val ICON_GAP = 6
private const val MAX_LENGTH = 32
private const val BAR_RADIUS = 6
private const val ALLOWED_SYMBOLS = "._- "
private const val PLACEHOLDER = "Search modules"

internal object SearchBar {

    private val expand = Animation(EXPAND_MS)
    private val input = TextField(::sanitize, MAX_LENGTH)

    var open = false
        private set

    val query: String get() = input.text

    private fun sanitize(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it in ALLOWED_SYMBOLS }

    fun rect(gui: ClickGUI): Rect {
        val right = SettingsHeader.accountPillRect(gui).x - TRAIL_GAP
        val w = expand.lerp(ICON, BAR_W)
        val h = expand.lerp(ICON, BAR_H)
        return Rect(right - w, gui.panelY + (SETTINGS_HEADER_H - h) / 2, w, h)
    }

    fun open() {
        open = true
        expand.set(true)
        input.selectAll()
    }

    fun close() {
        open = false
        expand.set(false)
        input.clear()
    }

    fun draw(ctx: GuiGraphicsExtractor, gui: ClickGUI, mx: Int, my: Int) {
        val r = rect(gui)
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        if (expand.value > 0f) {
            GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, BAR_RADIUS, cValueBox, if (open) cViolet else cCardEdge)
        }
        drawIcon(ctx, r)
        drawText(ctx, r)
    }

    private fun drawIcon(ctx: GuiGraphicsExtractor, r: Rect) {
        val x = r.x + expand.lerp(0, SIDE_PAD)
        val y = r.y + (r.h - ICON) / 2
        GobbyTextures.search(ctx, x, y, ICON, if (open) cInkSoft else cInkFaint)
    }

    private fun textLeft(r: Rect): Int = r.x + expand.lerp(0, SIDE_PAD) + ICON + ICON_GAP

    private fun textWidth(r: Rect): Int = r.x + r.w - SIDE_PAD - textLeft(r)

    private fun drawText(ctx: GuiGraphicsExtractor, r: Rect) {
        val available = textWidth(r)
        if (available <= 0) return
        val left = textLeft(r)
        ctx.enableScissor(left, r.y, left + available, r.y + r.h)
        TextFieldView.draw(
            ctx, input, left - scrollOffset(available), r.y, r.h,
            SETTINGS_VALUE_SCALE, cInk, open, placeholder = PLACEHOLDER
        )
        ctx.disableScissor()
    }

    private fun scrollOffset(available: Int): Int {
        val caretX = textWScaled(input.text.take(input.caret), SETTINGS_VALUE_SCALE)
        return (caretX - available).coerceAtLeast(0)
    }

    fun handleClick(gui: ClickGUI, mx: Int, my: Int): Boolean {
        val r = rect(gui)
        if ((mx to my) !in r) return false
        if (!open) {
            open()
            return true
        }
        if (mx <= r.x + expand.lerp(0, SIDE_PAD) + ICON) {
            close()
            return true
        }
        val available = textWidth(r)
        val origin = textLeft(r) - scrollOffset(available)
        input.placeCaret(TextFieldView.caretIndexAt(input.text, origin, mx, SETTINGS_VALUE_SCALE), Modifiers.shift())
        return true
    }

    fun handleDrag(gui: ClickGUI, mx: Int): Boolean {
        if (!open) return false
        val r = rect(gui)
        val origin = textLeft(r) - scrollOffset(textWidth(r))
        input.placeCaret(TextFieldView.caretIndexAt(input.text, origin, mx, SETTINGS_VALUE_SCALE), extend = true)
        return true
    }

    fun handleKey(key: Int): Boolean {
        if (!open) return false
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close()
            return true
        }
        return TextFieldKeys.handle(input, key, allowUndo = false)
    }

    fun handleChar(chr: Char): Boolean {
        if (!open || sanitize(chr.toString()).isEmpty()) return false
        input.insert(chr.toString())
        return true
    }

    fun openWith(chr: Char): Boolean {
        if (sanitize(chr.toString()).isEmpty()) return false
        open()
        input.clear()
        input.insert(chr.toString())
        return true
    }
}
