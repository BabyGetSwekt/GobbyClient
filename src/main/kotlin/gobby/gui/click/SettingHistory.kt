package gobby.gui.click

private const val MAX_UNDO = 32

object SettingHistory {

    private class Change(val setting: Setting<*>, val previous: Any?)

    private val changes = ArrayDeque<Change>()

    fun record(setting: Setting<*>) {
        changes.addLast(Change(setting, setting.value))
        if (changes.size > MAX_UNDO) changes.removeFirst()
    }

    fun undo(): Boolean {
        val change = changes.removeLastOrNull() ?: return false
        @Suppress("UNCHECKED_CAST")
        (change.setting as Setting<Any?>).value = change.previous
        ConfigManager.save()
        return true
    }

    fun clear() = changes.clear()
}
