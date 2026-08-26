package gobby.gui.screen.modhider

import gobby.Gobbyclient.Companion.mc
import gobby.features.skyblock.ModIdHider
import gobby.features.skyblock.ModIdRules
import gobby.gui.click.ClickGUI
import gobby.gui.click.TextField
import gobby.utils.Utils.executeLater
import gobby.utils.timer.Clock

private const val ID_MAX_LENGTH = 64
private const val SEARCH_MAX_LENGTH = 32
private const val ID_SYMBOLS = "-_."
private const val NOTICE_MS = 2600L
private const val SNITCH_NOTICE = "Why would u snitch on yourself?"

private fun modIdText(raw: String): String =
    raw.lowercase().filter { it.isLetterOrDigit() || it in ID_SYMBOLS }

internal class ModIdRow(var id: String)

internal object ModIdList {

    val rows = mutableListOf<ModIdRow>()
    val idField = TextField(::modIdText, ID_MAX_LENGTH)
    val searchField = TextField(::modIdText, SEARCH_MAX_LENGTH)

    var editing: ModIdRow? = null
        private set
    var searchFocused = false
        private set

    private var notice: String? = null
    private val noticeClock = Clock()

    fun load() {
        rows.clear()
        ModIdHider.getHiddenMods().forEach { rows += ModIdRow(it) }
        editing = null
        notice = null
    }

    fun close() {
        stopEditing()
        searchFocused = false
        searchField.clear()
        notice = null
    }

    fun isProtected(row: ModIdRow): Boolean = ModIdRules.isProtected(row.id)

    fun notice(): String? = notice?.takeIf { noticeClock.getTime() < NOTICE_MS }

    fun add() {
        val row = ModIdRow("")
        rows += row
        startEditing(row)
    }

    fun remove(row: ModIdRow) {
        if (isProtected(row)) return refuse()
        if (editing === row) editing = null
        rows.remove(row)
        commit()
    }

    private fun refuse() {
        notice = SNITCH_NOTICE
        noticeClock.update()
    }

    fun focusSearch() {
        stopEditing()
        searchFocused = true
    }

    fun blurSearch() {
        searchFocused = false
    }

    fun visibleRows(): List<ModIdRow> {
        val query = searchField.text.trim()
        if (query.isEmpty()) return rows
        return rows.filter { it.id.contains(query) }
    }

    fun startEditing(row: ModIdRow) {
        if (isProtected(row)) return refuse()
        searchFocused = false
        if (editing !== row) stopEditing()
        editing = row
        idField.reset(row.id)
    }

    fun stopEditing() {
        val row = editing ?: return
        editing = null
        val typed = idField.text.trim()
        if (typed.isEmpty() || rows.any { it !== row && it.id == typed }) rows.remove(row) else row.id = typed
        commit()
    }

    fun idOf(row: ModIdRow): String = if (editing === row) idField.text else row.id

    fun commit() {
        ModIdHider.replaceAll(rows.map { it.id }.filter { it.isNotEmpty() })
        ModIdHider.save()
    }
}

fun openModIdList() = mc.executeLater {
    val existing = mc.gui.screen() as? ClickGUI
    val screen = existing ?: ClickGUI().also { mc.gui.setScreen(it) }
    screen.openView(ModIdView, standalone = existing == null)
}
