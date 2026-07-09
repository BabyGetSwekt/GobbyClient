package gobby.features.render

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import gobby.Gobbyclient.Companion.logger
import java.awt.Color
import java.io.File

val DEFAULT_MOB_COLOR: Color = Color(255, 0, 0, 160)

enum class MobFilter(val label: String) {
    EQUALS("equals"),
    CONTAINS("contains");

    fun matches(candidate: String, target: String): Boolean = when (this) {
        EQUALS -> candidate.equals(target, ignoreCase = true)
        CONTAINS -> candidate.contains(target, ignoreCase = true)
    }
}

data class MobEntry(
    var enabled: Boolean = true,
    var name: String = "",
    var filter: MobFilter = MobFilter.CONTAINS,
    var color: Int = DEFAULT_MOB_COLOR.rgb
) {
    val isActive: Boolean get() = enabled && name.isNotBlank()

    val awtColor: Color get() = Color(color, true)

    fun matches(candidate: String): Boolean = isActive && filter.matches(candidate, name)
}

object MobHighlighterConfig {

    private val configFile = File("./config/gobbyclientFabric/mobHighlighter.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val listType = object : TypeToken<List<MobEntry>>() {}.type
    private val entries = mutableListOf<MobEntry>()

    init {
        load()
    }

    fun getEntries(): List<MobEntry> = entries.map { it.copy() }

    fun hasActiveEntries(): Boolean = entries.any { it.isActive }

    fun matches(candidate: String): Boolean = entries.any { it.matches(candidate) }

    fun colorFor(candidate: String): Int? = entries.firstOrNull { it.matches(candidate) }?.color

    fun replaceAll(newEntries: List<MobEntry>) {
        entries.clear()
        entries.addAll(newEntries.filter { it.name.isNotBlank() }.map { it.copy() })
    }

    fun save() {
        try {
            configFile.parentFile.mkdirs()
            configFile.writeText(gson.toJson(entries))
        } catch (e: Exception) {
            logger.error("Failed to save mob highlighter config", e)
        }
    }

    fun load() {
        entries.clear()
        if (!configFile.exists()) return
        try {
            val loaded: List<MobEntry> = gson.fromJson(configFile.readText(), listType) ?: emptyList()
            entries.addAll(loaded)
        } catch (e: Exception) {
            logger.error("Failed to load mob highlighter config", e)
        }
    }
}
