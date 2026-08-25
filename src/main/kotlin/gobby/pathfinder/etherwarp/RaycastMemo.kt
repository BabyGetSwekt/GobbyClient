package gobby.pathfinder.etherwarp

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import net.minecraft.core.BlockPos
import gobby.utils.HashMix.mix

internal class RaycastMemo(private val capacity: Int = DEFAULT_CAPACITY) {
    private val values = Long2LongOpenHashMap().apply { defaultReturnValue(MISSING) }

    fun reset() = values.clear()

    fun resolve(source: Long, rayIndex: Int, compute: () -> BlockPos?): BlockPos? {
        val key = key(source, rayIndex)
        val cached = values.get(key)
        if (cached != MISSING) return cached.takeUnless { it == NO_HIT }?.let(BlockPos::of)
        val result = compute()
        if (capacity > 0) {
            if (values.size >= capacity) evictOne()
            values.put(key, result?.asLong() ?: NO_HIT)
        }
        return result
    }

    private fun evictOne() {
        val iterator = values.long2LongEntrySet().fastIterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    private fun key(source: Long, rayIndex: Int): Long = mix(source xor (rayIndex.toLong() shl RAY_INDEX_SHIFT))


    private companion object {
        const val DEFAULT_CAPACITY = 262_144
        const val RAY_INDEX_SHIFT = 17
        const val MISSING = Long.MIN_VALUE
        const val NO_HIT = Long.MIN_VALUE + 1
    }
}
