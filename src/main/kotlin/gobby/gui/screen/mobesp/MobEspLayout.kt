package gobby.gui.screen.mobesp

import gobby.gui.click.ClickGUI
import gobby.gui.click.Rect

internal const val ADD_H = 20
internal const val ROW_H = 24
internal const val ROW_GAP = 4
private const val ROW_PAD = 8
private const val CHECK_SIZE = 13
private const val COL_GAP = 8
private const val FILTER_W = 62
private const val FILTER_H = 15
private const val SWATCH = 15
private const val REMOVE_W = 14
private const val ADD_SHARE = 3
private const val BAR_PARTS = 4
private const val SCROLL_TAIL = 6
private const val NAME_MIN_W = 40

internal object MobEspLayout {

    fun addRect(gui: ClickGUI) =
        Rect(gui.contentX, gui.contentY, (gui.contentW - COL_GAP) * ADD_SHARE / BAR_PARTS, ADD_H)

    fun searchRect(gui: ClickGUI): Rect {
        val left = addRect(gui).let { it.x + it.w + COL_GAP }
        return Rect(left, gui.contentY, gui.contentX + gui.contentW - left, ADD_H)
    }

    fun rowRect(gui: ClickGUI, index: Int): Rect {
        val top = gui.contentY + ADD_H + ROW_GAP + index * (ROW_H + ROW_GAP) + gui.scrollOffset.toInt()
        return Rect(gui.contentX, top, gui.contentW, ROW_H)
    }

    fun totalHeight(rows: Int): Int =
        ADD_H + ROW_GAP + rows * (ROW_H + ROW_GAP) + SCROLL_TAIL

    fun checkRect(r: Rect) = Rect(r.x + ROW_PAD, r.y + (r.h - CHECK_SIZE) / 2, CHECK_SIZE, CHECK_SIZE)

    fun removeRect(r: Rect) = Rect(r.x + r.w - ROW_PAD - REMOVE_W, r.y + (r.h - REMOVE_W) / 2, REMOVE_W, REMOVE_W)

    fun swatchRect(r: Rect) =
        Rect(removeRect(r).x - COL_GAP - SWATCH, r.y + (r.h - SWATCH) / 2, SWATCH, SWATCH)

    fun filterRect(r: Rect) =
        Rect(swatchRect(r).x - COL_GAP - FILTER_W, r.y + (r.h - FILTER_H) / 2, FILTER_W, FILTER_H)

    fun nameRect(r: Rect): Rect {
        val left = checkRect(r).let { it.x + it.w + COL_GAP }
        return Rect(left, r.y, (filterRect(r).x - COL_GAP - left).coerceAtLeast(NAME_MIN_W), r.h)
    }
}
