package gobby.gui.screen.pets

import gobby.gui.click.ClickGUI
import gobby.gui.click.Rect

internal const val BAR_H = 20
internal const val COL_GAP = 8
internal const val ROW_GAP = 4
private const val ROW_H = 24
private const val ROW_PAD = 8
private const val HEAD_SIZE = 16
private const val KEY_W = 58
private const val KEY_H = 15
private const val RESET_W = 14
private const val EQUIP_W = 46
private const val REFRESH_W = 78
private const val NAME_MIN_W = 40
private const val SCROLL_TAIL = 6
private const val TOGGLE_COUNT = 3

internal object PetsLayout {

    fun refreshRect(gui: ClickGUI) = Rect(gui.contentX, gui.contentY, REFRESH_W, BAR_H)

    fun searchRect(gui: ClickGUI) = Rect(
        gui.contentX + REFRESH_W + COL_GAP, gui.contentY, gui.contentW - REFRESH_W - COL_GAP, BAR_H
    )

    fun toggleRect(gui: ClickGUI, index: Int): Rect {
        val width = (gui.contentW - COL_GAP * (TOGGLE_COUNT - 1)) / TOGGLE_COUNT
        return Rect(gui.contentX + index * (width + COL_GAP), gui.contentY + BAR_H + ROW_GAP, width, BAR_H)
    }

    fun listTop(gui: ClickGUI) = gui.contentY + (BAR_H + ROW_GAP) * 2

    fun rowRect(gui: ClickGUI, index: Int) =
        Rect(gui.contentX, listTop(gui) + index * (ROW_H + ROW_GAP) + gui.scrollOffset.toInt(), gui.contentW, ROW_H)

    fun totalHeight(rows: Int) = (BAR_H + ROW_GAP) * 2 + rows * (ROW_H + ROW_GAP) + SCROLL_TAIL

    fun headRect(r: Rect) = Rect(r.x + ROW_PAD, r.y + (r.h - HEAD_SIZE) / 2, HEAD_SIZE, HEAD_SIZE)

    fun equipRect(r: Rect) = Rect(r.x + r.w - ROW_PAD - EQUIP_W, r.y + (r.h - KEY_H) / 2, EQUIP_W, KEY_H)

    fun resetRect(r: Rect) =
        Rect(equipRect(r).x - COL_GAP - RESET_W, r.y + (r.h - RESET_W) / 2, RESET_W, RESET_W)

    fun keyRect(r: Rect) = Rect(resetRect(r).x - COL_GAP - KEY_W, r.y + (r.h - KEY_H) / 2, KEY_W, KEY_H)

    fun nameRect(r: Rect): Rect {
        val left = headRect(r).let { it.x + it.w + COL_GAP }
        return Rect(left, r.y, (keyRect(r).x - COL_GAP - left).coerceAtLeast(NAME_MIN_W), r.h)
    }
}
