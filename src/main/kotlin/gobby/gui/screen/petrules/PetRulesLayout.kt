package gobby.gui.screen.petrules

import gobby.gui.click.ClickGUI
import gobby.gui.click.Rect

internal const val BAR_H = 20
internal const val COL_GAP = 8
internal const val ROW_GAP = 4
internal const val ROW_H = 24
private const val ROW_PAD = 8
private const val TRASH_W = 14
private const val ADD_SHARE = 3
private const val BAR_PARTS = 4
private const val SCROLL_TAIL = 6
private const val WHEN_MIN_W = 60
private const val POPUP_W = 260
private const val POPUP_ROW_H = 20
private const val POPUP_PAD = 8
private const val POPUP_HEAD_H = 22
private const val POPUP_MAX_ROWS = 9
private const val POPUP_CLOSE = 12
private const val POPUP_BAR_W = 3

internal object PetRulesLayout {

    fun addRect(gui: ClickGUI) =
        Rect(gui.contentX, gui.contentY, (gui.contentW - COL_GAP) * ADD_SHARE / BAR_PARTS, BAR_H)

    fun searchRect(gui: ClickGUI): Rect {
        val left = addRect(gui).let { it.x + it.w + COL_GAP }
        return Rect(left, gui.contentY, gui.contentX + gui.contentW - left, BAR_H)
    }

    fun listTop(gui: ClickGUI) = gui.contentY + BAR_H + ROW_GAP

    fun rowRect(gui: ClickGUI, index: Int) =
        Rect(gui.contentX, listTop(gui) + index * (ROW_H + ROW_GAP) + gui.scrollOffset.toInt(), gui.contentW, ROW_H)

    fun totalHeight(rows: Int) = BAR_H + ROW_GAP + rows * (ROW_H + ROW_GAP) + SCROLL_TAIL

    fun trashRect(r: Rect) = Rect(r.x + r.w - ROW_PAD - TRASH_W, r.y + (r.h - TRASH_W) / 2, TRASH_W, TRASH_W)

    fun petRect(r: Rect): Rect {
        val width = (r.w - ROW_PAD * 2 - TRASH_W - COL_GAP) / 2
        return Rect(trashRect(r).x - COL_GAP - width, r.y, width, r.h)
    }

    fun whenRect(r: Rect): Rect {
        val left = r.x + ROW_PAD
        return Rect(left, r.y, (petRect(r).x - COL_GAP - left).coerceAtLeast(WHEN_MIN_W), r.h)
    }

    fun popupRect(gui: ClickGUI, rows: Int): Rect {
        val height = POPUP_HEAD_H + POPUP_PAD + rows.coerceAtMost(POPUP_MAX_ROWS) * (POPUP_ROW_H + ROW_GAP) + POPUP_PAD
        return Rect(
            gui.contentX + (gui.contentW - POPUP_W) / 2,
            gui.contentY + (gui.contentH - height) / 2,
            POPUP_W,
            height
        )
    }

    fun popupRowRect(popup: Rect, index: Int, offset: Int) = Rect(
        popup.x + POPUP_PAD,
        popup.y + POPUP_HEAD_H + POPUP_PAD + (index - offset) * (POPUP_ROW_H + ROW_GAP),
        popup.w - POPUP_PAD * 2 - POPUP_BAR_W,
        POPUP_ROW_H
    )

    fun popupVisibleRows(popup: Rect) = (popup.h - POPUP_HEAD_H - POPUP_PAD * 2) / (POPUP_ROW_H + ROW_GAP)

    fun popupCloseRect(popup: Rect) = Rect(
        popup.x + popup.w - POPUP_PAD - POPUP_CLOSE,
        popup.y + (POPUP_HEAD_H - POPUP_CLOSE) / 2,
        POPUP_CLOSE,
        POPUP_CLOSE
    )

    fun popupBarRect(popup: Rect, rows: Int, offset: Int): Rect? {
        val visible = popupVisibleRows(popup)
        if (rows <= visible) return null
        val track = rows * (POPUP_ROW_H + ROW_GAP)
        val top = popup.y + POPUP_HEAD_H + POPUP_PAD
        val height = (visible * (POPUP_ROW_H + ROW_GAP)).let { it * it / track }
        val travel = visible * (POPUP_ROW_H + ROW_GAP) - height
        return Rect(
            popup.x + popup.w - POPUP_PAD / 2 - POPUP_BAR_W,
            top + travel * offset / (rows - visible),
            POPUP_BAR_W,
            height
        )
    }
}
