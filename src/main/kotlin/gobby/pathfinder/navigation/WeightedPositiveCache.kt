package gobby.pathfinder.navigation

import java.util.LinkedHashMap

internal class WeightedPositiveCache<K, V>(
    private val maxBytes: Long,
    private val weightOf: (V) -> Long
) {
    private val entries = LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    var bytes: Long = 0
        private set
    var evictions: Long = 0
        private set
    var rejections: Long = 0
        private set

    fun get(key: K): V? = entries[key]

    fun put(key: K, value: V) {
        val weight = weightOf(value)
        if (weight > maxBytes) {
            rejections++
            return
        }
        entries.put(key, value)?.let { bytes -= weightOf(it) }
        bytes += weight
        while (bytes > maxBytes && entries.isNotEmpty()) evictEldest()
    }

    private fun evictEldest() {
        val iterator = entries.entries.iterator()
        bytes -= weightOf(iterator.next().value)
        iterator.remove()
        evictions++
    }

    fun remove(key: K) = entries.remove(key)?.also { bytes -= weightOf(it) }

    fun removeIfCurrent(key: K, expected: V) {
        if (entries[key] === expected) remove(key)
    }

    fun replaceIfCurrent(key: K, expected: V, replacement: V) {
        if (entries[key] === expected) put(key, replacement)
    }

    fun clear() {
        entries.clear()
        bytes = 0
        evictions = 0
        rejections = 0
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
