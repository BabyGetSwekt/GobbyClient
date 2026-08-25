package gobby.gui.components

class UnsavedChangesPolicy {

    private var bypass = false

    var dirty = false

    fun allowClose() {
        bypass = true
    }

    fun requiresPrompt(): Boolean = dirty && !bypass
}
