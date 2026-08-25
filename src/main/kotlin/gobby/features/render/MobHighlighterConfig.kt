package gobby.features.render

import java.awt.Color
import gobby.utils.ConfigUtils

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

    private val config = ConfigUtils.makeConfig("mobHighlighter") { mutableListOf<MobEntry>() }
    private val mobs get() = config.data

    fun getEntries(): List<MobEntry> = mobs.map { it.copy() }

    fun hasActiveEntries(): Boolean = mobs.any { it.isActive }

    fun matches(candidate: String): Boolean = mobs.any { it.matches(candidate) }

    fun colorFor(candidate: String): Int? = mobs.firstOrNull { it.matches(candidate) }?.color

    fun replaceAll(newEntries: List<MobEntry>) {
        mobs.clear()
        mobs.addAll(newEntries.filter { it.name.isNotBlank() }.map { it.copy() })
    }

    fun save() = config.save()

    fun load() {
        config.reload()
    }
}
