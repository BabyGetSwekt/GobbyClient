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
        val keys = (minChunkX..maxChunkX).asSequence()
            .flatMap { x -> (minChunkZ..maxChunkZ).asSequence().map { z -> x to z } }
            .toList()
        val captured = keys.count { (cx, cz) ->
            val key = chunkKey(cx, cz)
            (refresh || key in dirtyChunks || !snapshots.containsKey(key)) && loaded(cx, cz) && captureAndStore(cx, cz) != null
        }
        CacheDiagnostics.log("capture-range chunks=${keys.size} captured=$captured refresh=$refresh snapshots=${snapshots.size} dirty=${dirtyChunks.size}")
    }

    fun captureAndStore(chunk: LevelChunk): BlockCache.ChunkSnapshot? {
        if (chunk.isEmpty) return snapshots[chunkKey(chunk.pos.x, chunk.pos.z)]
        val snapshot = runCatching { BlockCacheCapture.capture(chunk) }.getOrNull() ?: return null
        val key = chunkKey(chunk.pos.x, chunk.pos.z)
        synchronized(lock) {
            snapshots[key] = snapshot
            dirtyChunks.remove(key)
            markChanged(chunk.pos.x, chunk.pos.z)
        }
        reportedMissing.remove(key)
        reportedDirty.remove(key)
        CacheDiagnostics.log("capture chunk=${chunk.pos.x},${chunk.pos.z} sections=${snapshot.sections.count { it != null }} snapshots=${snapshots.size} dirty=${dirtyChunks.size}")
        return snapshot
    }

    fun captureAndStore(cx: Int, cz: Int): BlockCache.ChunkSnapshot? =
        mc.level?.chunkSource?.getChunk(cx, cz, false)?.let(::captureAndStore)

    fun freeze(): BlockCache.SnapshotView = synchronized(lock) {
        val minY = snapshots.values.minOfOrNull { it.minBlockY } ?: 0
        val maxY = snapshots.values.maxOfOrNull { it.minBlockY + it.sections.size * 16 } ?: 0
        BlockCache.SnapshotView(snapshots.toMap(), passableOverrides.toSet(), chunkVersions.toMap(), generation, epoch, minY, maxY)
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

    fun debugChunk(x: Int, z: Int, loaded: (Int, Int) -> Boolean): String {
        val cx = x shr 4
        val cz = z shr 4
        val key = chunkKey(cx, cz)
        return "chunk=$cx,$cz loaded=${loaded(x, z)} snapshot=${hasSnapshot(x, z)} dirty=${key in dirtyChunks}"
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
    }

    fun writeState(reason: String, columns: Int): Unit {
        val lines = mutableListOf("reason=$reason", "snapshots=${snapshots.size} dirty=${dirtyChunks.size} columns=$columns")
        snapshots.entries.sortedBy { it.key }.forEach { (key, snapshot) ->
            lines.add("chunk=${key shr 32},${key.toInt()} snapshot=${key !in dirtyChunks} dirty=${key in dirtyChunks} sections=${snapshot.sections.count { it != null }}")
        }
        CacheDiagnostics.writeState(lines)
    }

    private fun updateOverride(pos: BlockPos, add: Boolean) {
        synchronized(lock) {
            val changed = if (add) passableOverrides.add(pos.asLong()) else passableOverrides.remove(pos.asLong())
            if (changed) markChanged(pos.x shr 4, pos.z shr 4)
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
    }

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
}
