package gobby.pathfinder.world

import gobby.Gobbyclient.Companion.mc
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap

internal class BlockCacheStore {
    private val air = Blocks.AIR.defaultBlockState()
    private val snapshots = ConcurrentHashMap<Long, BlockCache.ChunkSnapshot>()
    private val dirtyChunks = ConcurrentHashMap.newKeySet<Long>()
    private val chunkVersions = ConcurrentHashMap<Long, Long>()
    private val passableOverrides = ConcurrentHashMap.newKeySet<Long>()
    private val reportedMissing = ConcurrentHashMap.newKeySet<Long>()
    private val reportedDirty = ConcurrentHashMap.newKeySet<Long>()
    private val lock = Any()

    @Volatile private var generation = 0L

    @Volatile private var epoch = 0L

    @Volatile private var published: BlockCache.SnapshotView? = null

    @Volatile private var publicationDirty = true

    fun markPassable(pos: BlockPos) {
        updateOverride(pos, true)
    }

    fun unmarkPassable(pos: BlockPos) {
        updateOverride(pos, false)
    }

    fun getBlockState(pos: BlockPos): BlockState = if (pos.asLong() in passableOverrides) air
    else snapshotFor(pos.x shr 4, pos.z shr 4)?.blockState(pos.x, pos.y, pos.z) ?: air

    fun snapshotState(pos: BlockPos): BlockState? = if (pos.asLong() in passableOverrides) air
    else snapshots[chunkKey(pos.x shr 4, pos.z shr 4)]?.blockState(pos.x, pos.y, pos.z)

    fun isPassableOverride(pos: BlockPos): Boolean = pos.asLong() in passableOverrides

    fun captureLoadedChunks(minChunkX: Int, minChunkZ: Int, maxChunkX: Int, maxChunkZ: Int, refresh: Boolean, loaded: (Int, Int) -> Boolean) {
        val width = maxChunkX - minChunkX + 1
        val depth = maxChunkZ - minChunkZ + 1
        val inspected = width * depth
        var captured = 0
        repeat(inspected) { index ->
            val cx = minChunkX + index / depth
            val cz = minChunkZ + index % depth
            val key = chunkKey(cx, cz)
            if ((refresh || key in dirtyChunks || !snapshots.containsKey(key)) && loaded(cx, cz) && captureAndStore(cx, cz) != null) captured++
        }
    }

    fun flushDirtyLoadedChunks(loaded: (Int, Int) -> Boolean): Int {
        if (!mc.isSameThread) return 0
        return dirtyChunks.count { key ->
            val chunkX = (key shr 32).toInt()
            val chunkZ = key.toInt()
            loaded(chunkX, chunkZ) && captureAndStore(chunkX, chunkZ) != null
        }
    }

    fun captureAndStore(chunk: LevelChunk): BlockCache.ChunkSnapshot? {
        if (chunk.isEmpty) return snapshots[chunkKey(chunk.pos.x, chunk.pos.z)]
        val key = chunkKey(chunk.pos.x, chunk.pos.z)
        val previous = snapshots[key]
        val snapshot = runCatching { BlockCacheCapture.capture(chunk, previous) }.getOrNull() ?: return null
        val changed = previous?.collisionFingerprint != snapshot.collisionFingerprint
        synchronized(lock) {
            snapshots[key] = snapshot
            dirtyChunks.remove(key)
            if (changed) markChanged(chunk.pos.x, chunk.pos.z)
            publicationDirty = true
        }
        reportedMissing.remove(key)
        reportedDirty.remove(key)
        return snapshot
    }

    fun captureAndStore(cx: Int, cz: Int): BlockCache.ChunkSnapshot? =
        mc.level?.chunkSource?.getChunk(cx, cz, false)?.let(::captureAndStore)

    fun freeze(): BlockCache.SnapshotView = synchronized(lock) {
        if (publicationDirty || published == null) publishSnapshot()
        published ?: error("Block cache snapshot publication failed")
    }

    fun version(): Long = generation

    fun dependenciesUnchanged(snapshot: BlockCache.SnapshotView, dependencies: Set<Long>): Boolean = synchronized(lock) {
        snapshot.isInEpoch(epoch) && dependencies.all { snapshot.versionOf(it) == (chunkVersions[it] ?: 0L) }
    }

    fun publishIfCurrent(snapshot: BlockCache.SnapshotView, dependencies: Set<Long>, publish: () -> Unit): Boolean = synchronized(lock) {
        if (!dependenciesUnchanged(snapshot, dependencies)) return@synchronized false
        publish()
        true
    }

    fun isAvailable(x: Int, z: Int, loaded: (Int, Int) -> Boolean): Boolean = loaded(x shr 4, z shr 4) || hasSnapshot(x, z)

    fun hasSnapshot(x: Int, z: Int): Boolean {
        val key = chunkKey(x shr 4, z shr 4)
        return key !in dirtyChunks && snapshots.containsKey(key)
    }


    fun reportMissing(cx: Int, cz: Int): Boolean = reportedMissing.add(chunkKey(cx, cz))

    fun invalidate(cx: Int, cz: Int) {
        synchronized(lock) {
            if (!dirtyChunks.add(chunkKey(cx, cz))) return@synchronized
            markChanged(cx, cz)
        }
    }

    fun clear() = synchronized(lock) {
        epoch++
        generation++
        snapshots.clear()
        dirtyChunks.clear()
        chunkVersions.clear()
        reportedMissing.clear()
        reportedDirty.clear()
        passableOverrides.clear()
        published = null
        publicationDirty = true
    }


    private fun updateOverride(pos: BlockPos, add: Boolean) {
        synchronized(lock) {
            val changed = if (add) passableOverrides.add(pos.asLong()) else passableOverrides.remove(pos.asLong())
            if (changed) {
                markChanged(pos.x shr 4, pos.z shr 4)
                publicationDirty = true
            }
        }
    }

    internal fun snapshotFor(cx: Int, cz: Int): BlockCache.ChunkSnapshot? {
        val key = chunkKey(cx, cz)
        if (key in dirtyChunks) {
            if (!mc.isSameThread) return snapshots[key]
            val chunk = mc.level?.chunkSource?.getChunk(cx, cz, false) ?: return snapshots[key]
            return captureAndStore(chunk) ?: snapshots[key]
        }
        snapshots[key]?.let { return it }
        if (!mc.isSameThread) return null
        val chunk = mc.level?.chunkSource?.getChunk(cx, cz, false) ?: return null
        return captureAndStore(chunk)
    }

    private fun markChanged(cx: Int, cz: Int) {
        val key = chunkKey(cx, cz)
        chunkVersions[key] = (chunkVersions[key] ?: 0L) + 1L
        generation++
        publicationDirty = true
    }

    private fun publishSnapshot() {
        val usable = snapshots.filterKeys { it !in dirtyChunks }
        val minY = usable.values.minOfOrNull { it.minBlockY } ?: 0
        val maxY = usable.values.maxOfOrNull { it.minBlockY + it.sections.size * 16 } ?: 0
        published = BlockCache.SnapshotView(usable, passableOverrides.toSet(), chunkVersions.toMap(), generation, epoch, minY, maxY)
        publicationDirty = false
    }

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
}
