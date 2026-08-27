package gobby.gui.click

import gobby.utils.render.CursorStyle
import net.minecraft.client.gui.GuiGraphicsExtractor

internal const val SEARCH_PAD = 6
internal const val SEARCH_ICON = 11
private const val PLUS_ICON = 7
private const val CONTROL_RADIUS = 5
private const val ICON_GAP = 8

/**
 * A view with a search bar over a scrolling list. Every drawing method is open, so a view that
 * wants a different look keeps the behaviour and replaces only the paint.
 */
internal abstract class SearchableView : ClickView {

    protected abstract val searchField: TextField

    protected abstract val searchFocused: Boolean

    private var shownQuery = ""

    /**
     * Sends the list back to the top whenever the query changed since the previous frame.
     */
    protected fun followSearch(gui: ClickGUI) {
        if (searchField.text == shownQuery) return
        shownQuery = searchField.text
        gui.resetScroll()
    }

    protected fun searchTextX(r: Rect): Int = r.x + SEARCH_PAD + SEARCH_ICON + SEARCH_PAD

    protected fun placeSearchCaret(r: Rect, mx: Int) =
        searchField.placeCaret(TextFieldView.caretIndexAt(searchField.text, searchTextX(r), mx, SETTINGS_VALUE_SCALE), extend = false)

    protected open fun drawSearch(ctx: GuiGraphicsExtractor, r: Rect, mx: Int, my: Int, placeholder: String = "Search") {
        CursorStyle.requestHandIf((mx to my) in r)
        GobbyDraw.roundedBox(ctx, r.x, r.y, r.w, r.h, CONTROL_RADIUS, cValueBox, if (searchFocused) cViolet else cCardEdge)
        GobbyTextures.search(ctx, r.x + SEARCH_PAD, r.y + (r.h - SEARCH_ICON) / 2, SEARCH_ICON, if (searchFocused) cInkSoft else cInkFaint)
        val textX = searchTextX(r)
        ctx.enableScissor(textX, r.y, r.x + r.w - SEARCH_PAD, r.y + r.h)
        TextFieldView.draw(ctx, searchField, textX, r.y, r.h, SETTINGS_VALUE_SCALE, cInk, searchFocused, placeholder = placeholder)
        ctx.disableScissor()
    }

    protected open fun drawAdd(ctx: GuiGraphicsExtractor, r: Rect, label: String, mx: Int, my: Int) {
        val hovered = (mx to my) in r
        CursorStyle.requestHandIf(hovered)
        GobbyDraw.roundedRect(ctx, r.x, r.y, r.w, r.h, CONTROL_RADIUS, if (hovered) cViolet else cValueBox)
        val labelW = textWScaled(label, SETTINGS_VALUE_SCALE)
        val plusX = r.x + (r.w - labelW - PLUS_ICON - ICON_GAP) / 2
        GobbyTextures.plus(ctx, plusX, r.y + (r.h - PLUS_ICON) / 2, PLUS_ICON, cInk)
        val textH = (tr.lineHeight * SETTINGS_VALUE_SCALE).toInt()
        drawTextScaled(ctx, plusX + PLUS_ICON + ICON_GAP, r.y + (r.h - textH) / 2, label, SETTINGS_VALUE_SCALE, cInk, false)
    }
}
