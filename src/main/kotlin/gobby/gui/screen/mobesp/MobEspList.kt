package gobby.gui.screen.mobesp

import gobby.gui.click.*

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.executeLater
import gobby.features.render.DEFAULT_MOB_COLOR
import gobby.features.render.MobEntry
import gobby.features.render.MobFilter
import gobby.features.render.MobHighlighterConfig
import java.awt.Color

private const val NAME_MAX_LENGTH = 48
private const val SEARCH_MAX_LENGTH = 32
private const val NAME_SYMBOLS = " ._:/-'&"

private fun mobText(raw: String): String = raw.filter { it.isLetterOrDigit() || it in NAME_SYMBOLS }

internal class MobRow(val entry: MobEntry) {
    val color = ColorSetting("Color", Color(entry.color, true))
}

internal object MobEspList {

    val rows = mutableListOf<MobRow>()
    val nameField = TextField(::mobText, NAME_MAX_LENGTH)
    val searchField = TextField(::mobText, SEARCH_MAX_LENGTH)

    var searchFocused = false
        private set

    var editing: MobRow? = null
        private set

    fun load() {
        rows.clear()
        MobHighlighterConfig.getEntries().forEach { rows += MobRow(it) }
        editing = null
    }

    fun close() {
        stopEditing()
        searchFocused = false
        searchField.clear()
        rows.forEach { it.color.expanded = false }
    }

    fun focusSearch() {
        stopEditing()
        searchFocused = true
    }

    fun blurSearch() {
        searchFocused = false
    }

    fun visibleRows(): List<MobRow> {
        val query = searchField.text.trim().lowercase()
        if (query.isEmpty()) return rows
        return rows.filter { it.entry.name.lowercase().contains(query) }
    }

    fun add() {
        rows += MobRow(MobEntry(color = rows.lastOrNull()?.entry?.color ?: DEFAULT_MOB_COLOR.rgb))
        commit()
    }

    fun remove(row: MobRow) {
        if (editing === row) editing = null
        row.color.expanded = false
        rows.remove(row)
        commit()
    }

    fun toggle(row: MobRow) {
        row.entry.enabled = !row.entry.enabled
        commit()
    }

    fun cycleFilter(row: MobRow) {
        row.entry.filter = if (row.entry.filter == MobFilter.EQUALS) MobFilter.CONTAINS else MobFilter.EQUALS
        commit()
    }

    fun startEditing(row: MobRow) {
        searchFocused = false
        if (editing !== row) stopEditing()
        editing = row
        nameField.reset(row.entry.name)
    }

    fun stopEditing() {
        val row = editing ?: return
        row.entry.name = nameField.text.trim()
        editing = null
        commit()
    }

    fun nameOf(row: MobRow): String = if (editing === row) nameField.text else row.entry.name

    fun commit() {
        rows.forEach { it.entry.color = it.color.value.rgb }
        editing?.let { it.entry.name = nameField.text.trim() }
        MobHighlighterConfig.replaceAll(rows.map { it.entry.copy() })
        MobHighlighterConfig.save()
    }
}

fun openMobEspList() = mc.executeLater {
    val existing = mc.gui.screen() as? ClickGUI
    val screen = existing ?: ClickGUI().also { mc.gui.setScreen(it) }
    screen.openView(MobEspView, standalone = existing == null)
}
