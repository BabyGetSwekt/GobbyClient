package gobby.gui.click

import gobby.utils.timer.Clock

private const val BLINK_MS = 530L

class TextField(private val sanitize: (String) -> String, var maxLength: Int) {

    var text = ""
        private set
    var caret = 0
        private set
    var anchor = 0
        private set

    private var undoText: String? = null
    private var undoCaret = 0
    private val blink = Clock()

    val selectionStart: Int get() = minOf(caret, anchor)
    val selectionEnd: Int get() = maxOf(caret, anchor)
    val hasSelection: Boolean get() = caret != anchor

    fun reset(value: String) {
        text = sanitize(value).take(maxLength)
        selectAll()
        undoText = null
    }

    fun clear() {
        text = ""
        placeCaret(0, extend = false)
        undoText = null
    }

    fun selectAll() {
        anchor = 0
        caret = text.length
        blink.update()
    }

    fun placeCaret(index: Int, extend: Boolean) {
        caret = index.coerceIn(0, text.length)
        if (!extend) anchor = caret
        blink.update()
    }

    fun insert(chars: String) {
        val allowed = sanitize(chars)
        if (allowed.isEmpty()) return
        rememberForUndo()
        val kept = removeSelection()
        val added = allowed.take((maxLength - kept.length).coerceAtLeast(0))
        if (added.isEmpty()) return restore(kept)
        text = kept.substring(0, caret) + added + kept.substring(caret)
        placeCaret(caret + added.length, extend = false)
    }

    fun replaceAll(replacement: String) {
        rememberForUndo()
        text = sanitize(replacement).take(maxLength)
        placeCaret(text.length, extend = false)
    }

    fun deleteBackward() {
        rememberForUndo()
        if (hasSelection) return restore(removeSelection())
        if (caret == 0) return
        text = text.removeRange(caret - 1, caret)
        placeCaret(caret - 1, extend = false)
    }

    fun deleteForward() {
        rememberForUndo()
        if (hasSelection) return restore(removeSelection())
        if (caret >= text.length) return
        text = text.removeRange(caret, caret + 1)
        placeCaret(caret, extend = false)
    }

    fun selectedText(): String = if (hasSelection) text.substring(selectionStart, selectionEnd) else text

    fun undo() {
        val previous = undoText ?: return
        undoText = null
        text = previous
        placeCaret(undoCaret, extend = false)
    }

    fun caretVisible(): Boolean = (blink.getTime() / BLINK_MS) % 2 == 0L

    private fun rememberForUndo() {
        undoText = text
        undoCaret = caret
    }

    private fun removeSelection(): String {
        if (!hasSelection) return text
        val kept = text.removeRange(selectionStart, selectionEnd)
        caret = selectionStart
        anchor = caret
        return kept
    }

    private fun restore(value: String) {
        text = value
        placeCaret(caret, extend = false)
    }
}
