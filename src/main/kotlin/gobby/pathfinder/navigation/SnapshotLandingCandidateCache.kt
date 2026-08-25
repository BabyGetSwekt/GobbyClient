package gobby.pathfinder.navigation

import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos

internal object SnapshotLandingCandidateCache {
    private val entries = WeightedPositiveCache<Key, Entry>(MAX_BYTES, Entry::estimatedBytes)
    private val rooms = WeightedPositiveCache<RoomKey, List<BlockPos>>(MAX_BYTES) { BASE_BYTES + it.size * POSITION_BYTES }

    @Synchronized
    fun get(snapshot: BlockCache.SnapshotView, seed: BlockPos): Entry? =
        entries.get(Key(snapshot.worldEpoch, snapshot.cacheVersion, seed.asLong()))

    @Synchronized
    fun put(snapshot: BlockCache.SnapshotView, seed: BlockPos, candidates: List<BlockPos>, chunks: List<Long>) {
        entries.put(Key(snapshot.worldEpoch, snapshot.cacheVersion, seed.asLong()), Entry(candidates, chunks))
    }

    @Synchronized
    fun room(snapshot: BlockCache.SnapshotView, component: List<Int>): List<BlockPos>? =
        rooms.get(RoomKey(snapshot.worldEpoch, snapshot.cacheVersion, component))

    @Synchronized
    fun putRoom(snapshot: BlockCache.SnapshotView, component: List<Int>, candidates: List<BlockPos>) {
        rooms.put(RoomKey(snapshot.worldEpoch, snapshot.cacheVersion, component), candidates)
    }

    @Synchronized
    fun clear() {
        entries.clear()
        rooms.clear()
    }

    data class Entry(val candidates: List<BlockPos>, val chunks: List<Long>) {
        val estimatedBytes: Long get() = BASE_BYTES + candidates.size * POSITION_BYTES + chunks.size * CHUNK_BYTES
    }

    private data class Key(val worldEpoch: Long, val cacheVersion: Long, val seed: Long)
    private data class RoomKey(val worldEpoch: Long, val cacheVersion: Long, val component: List<Int>)

    private const val MAX_BYTES = 4L * 1024L * 1024L
    private const val BASE_BYTES = 32L
    private const val POSITION_BYTES = 16L
    private const val CHUNK_BYTES = 8L
}
