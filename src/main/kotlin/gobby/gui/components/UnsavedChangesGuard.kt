package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.font.FontProvider

class UnsavedChangesGuard(
    window: UIComponent,
    message: String,
    discardText: String,
    keepText: String,
    font: FontProvider? = null,
    onDiscard: () -> Unit,
    private val onKeep: () -> Unit = {}
) {
    private val policy = UnsavedChangesPolicy()
    private val modal = ConfirmModal(window, message, discardText, keepText, font, onDiscard, { keep() })

    var dirty: Boolean
        get() = policy.dirty
        set(value) {
            policy.dirty = value
        }

    fun requestClose(close: () -> Unit) {
        if (policy.requiresPrompt()) modal.show() else close()
    }

    fun shouldBlockClose(): Boolean {
        if (!policy.requiresPrompt()) return false
        modal.show()
        return true
    }

    fun allowClose() = policy.allowClose()

    private fun keep() {
        modal.dismiss()
        onKeep()
    }
}
