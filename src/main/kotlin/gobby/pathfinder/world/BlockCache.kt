package gobby.pathfinder.world

import gobby.Gobbyclient.Companion.mc
import gobby.events.util.ChunkScopedCache
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.map.DungeonDoorDetector
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap

object BlockCache : ChunkScopedCache() {
    internal class ChunkSnapshot(
        val minBlockY: Int,
        val sections: Array<PalettedContainer<BlockState>?>,
        val surfaceHeights: IntArray,
        val nonAirBlocks: List<BlockPos>,
        val revision: Long,
        val collisionFingerprint: Long
    ) {
        fun blockState(x: Int, y: Int, z: Int): BlockState {
            val container = sections.getOrNull((y - minBlockY) shr 4) ?: return AIR
            return container.get(x and 15, y and 15, z and 15)
        }

        fun isSectionAir(y: Int): Boolean = sections.getOrNull((y - minBlockY) shr 4) == null

        fun sectionFloorY(y: Int): Int = minBlockY + (((y - minBlockY) shr 4) shl 4)

        fun surfaceY(x: Int, z: Int): Int = surfaceHeights[((z and 15) shl 4) or (x and 15)]
    }

    class SnapshotView internal constructor(
        private val snapshots: Map<Long, ChunkSnapshot>,
        private val passableOverrides: Set<Long>,
        private val chunkVersions: Map<Long, Long>,
        val cacheVersion: Long,
        val worldEpoch: Long,
        val minY: Int,
        val maxY: Int,
        private val dependencies: MutableSet<Long>? = null,
        private val missingChunks: MutableSet<Long>? = null,
        private val directStates: Map<Long, BlockState>? = null,
        private val directChunks: Set<Long> = emptySet(),
        private val sharedDirectIndex: SnapshotDirectIndex? = null
    ) {
        private val directIndex = sharedDirectIndex ?: SnapshotDirectIndex(directStates)
        internal val isDirectSnapshot: Boolean get() = directStates != null
        internal val canCacheLandingCandidates: Boolean get() = passableOverrides.isEmpty()
        internal val isTracking: Boolean get() = dependencies != null && missingChunks != null

        fun hasSnapshot(x: Int, z: Int): Boolean = directStates != null && blockCacheChunkKey(x shr 4, z shr 4) in directChunks || snapshots.containsKey(blockCacheChunkKey(x shr 4, z shr 4))

        fun knownChunkKeys(): Set<Long> = if (directStates == null) snapshots.keys else directChunks

        fun nonAirCandidates(key: Long): List<BlockPos> {
            dependencies?.add(key)
            return directIndex.nonAirCandidates(key).ifEmpty { snapshots[key]?.nonAirBlocks.orEmpty() }
        }

        internal fun peekNonAirCandidates(key: Long): List<BlockPos> = directIndex.nonAirCandidates(key).ifEmpty { snapshots[key]?.nonAirBlocks.orEmpty() }

        fun blockYRange(x: Int, z: Int): IntRange? = if (directStates != null && blockCacheChunkKey(x shr 4, z shr 4) in directChunks) {
            minY..(directIndex.surfaceHeight(x, z)?.plus(1) ?: maxY)
        } else snapshots[blockCacheChunkKey(x shr 4, z shr 4)]?.let { it.minBlockY..it.surfaceY(x, z) }

        fun isPassableOverride(pos: BlockPos): Boolean = isPassableOverride(pos.asLong())

        internal fun isPassableOverride(key: Long): Boolean = key in passableOverrides

        fun stateAt(pos: BlockPos): BlockState? = stateAt(pos.x, pos.y, pos.z)

        fun stateAt(x: Int, y: Int, z: Int): BlockState? {
            val key = blockCacheChunkKey(x shr 4, z shr 4)
            dependencies?.add(key)
            if (BlockPos.asLong(x, y, z) in passableOverrides) return AIR
            directStates?.let { return it[BlockPos.asLong(x, y, z)] ?: AIR }
            return snapshots[key]?.blockState(x, y, z) ?: recordMissing(key)
        }

        fun getBlockState(pos: BlockPos): BlockState? = stateAt(pos)

        internal fun dependencyKeys(): Set<Long> = dependencies?.toSet().orEmpty()

        internal fun recordDependencies(keys: Iterable<Long>) {
            dependencies?.addAll(keys)
        }

        internal fun chunkSnapshot(chunkX: Int, chunkZ: Int): ChunkSnapshot? {
            val key = blockCacheChunkKey(chunkX, chunkZ)
            dependencies?.add(key)
            val value = snapshots[key]
            if (directStates != null && key in directChunks) return null
            if (value == null) missingChunks?.add(key)
            return value
        }

        fun trackingMissingChunks(): SnapshotView {
            if (isTracking) return this
            return SnapshotView(
                snapshots, passableOverrides, chunkVersions, cacheVersion, worldEpoch, minY, maxY,
                ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet(), directStates, directChunks, directIndex
            )
        }

        fun missingChunksAccessed(): Set<Long> = missingChunks?.toSet().orEmpty()

        fun chunksAccessed(): Set<Long> = dependencies?.toSet().orEmpty()

        fun chunkRevision(key: Long): Long? = if (directStates != null) directChunks.takeIf { key in it }?.let { directIndex.chunkFingerprint(key) ?: EMPTY_FINGERPRINT } else snapshots[key]?.revision ?: recordMissing(key)

        fun chunkCollisionFingerprint(key: Long): Long? = if (directStates != null) directChunks.takeIf { key in it }?.let { directIndex.chunkFingerprint(key) ?: EMPTY_FINGERPRINT } else snapshots[key]?.collisionFingerprint ?: recordMissing(key)

        fun passableOverridesInChunks(keys: Set<Long>): Set<Long> = passableOverrides
            .asSequence()
            .filter { blockCacheChunkKey(BlockPos.of(it).x shr 4, BlockPos.of(it).z shr 4) in keys }
            .toSet()

        internal fun isolatedTrackingReads(): SnapshotView = SnapshotView(
            snapshots,
            passableOverrides,
            chunkVersions,
            cacheVersion,
            worldEpoch,
            minY,
            maxY,
            ConcurrentHashMap.newKeySet(),
            ConcurrentHashMap.newKeySet(),
            directStates,
            directChunks,
            directIndex
        )

        internal fun mergeAccessedReads(view: SnapshotView) {
            dependencies?.addAll(view.chunksAccessed())
            missingChunks?.addAll(view.missingChunksAccessed())
        }

        internal fun versionOf(key: Long): Long = chunkVersions[key] ?: 0L

        internal fun isInEpoch(currentEpoch: Long): Boolean = worldEpoch == currentEpoch

        private fun recordMissing(key: Long): Nothing? {
            missingChunks?.add(key)
            return null
        }
    }

    data class StandSurface(val pos: BlockPos, val feetY: Double)
    private val store = BlockCacheStore()
    private val geometry = BlockCacheGeometry(store::getBlockState, store::snapshotFor)
    private val doorBlocks = DungeonDoorDetector.ENTRANCE_DOOR_BLOCKS + setOf(Blocks.COAL_BLOCK, Blocks.DYED_TERRACOTTA.red())

    const val STEP_HEIGHT = 0.6
    const val MAX_JUMP_RISE = 1.25
    const val PLAYER_WIDTH = 0.6
    const val PLAYER_HEIGHT = 1.8
    const val PLAYER_HALF_WIDTH = PLAYER_WIDTH / 2.0

    fun markPassable(pos: BlockPos) {
        store.markPassable(pos)
        geometry.invalidateChunk(pos.x shr 4, pos.z shr 4)
    }

    fun unmarkPassable(pos: BlockPos) {
        store.unmarkPassable(pos)
        geometry.invalidateChunk(pos.x shr 4, pos.z shr 4)
    }

    fun isPassableOverride(pos: BlockPos): Boolean = store.isPassableOverride(pos)

    fun getBlockState(pos: BlockPos): BlockState = store.getBlockState(pos)

    fun isChunkAvailableLive(x: Int, z: Int): Boolean = isChunkLoaded(x, z)

    fun knownStateAt(pos: BlockPos): BlockState? = when {
        isChunkLoaded(pos.x, pos.z) -> mc.level?.getBlockState(pos)
        hasSnapshot(pos.x, pos.z) -> store.getBlockState(pos)
        else -> null
    }

    fun captureLoadedChunks(minChunkX: Int, minChunkZ: Int, maxChunkX: Int, maxChunkZ: Int, refresh: Boolean = false) {
        if (!canCaptureDungeonChunks()) return
        store.captureLoadedChunks(minChunkX, minChunkZ, maxChunkX, maxChunkZ, refresh, ::isChunkLoadedChunk)
    }

    fun flushDirtyLoadedChunks(): Int {
        if (!canCaptureDungeonChunks()) return 0
        return store.flushDirtyLoadedChunks(::isChunkLoadedChunk)
    }

    fun freeze(): SnapshotView = store.freeze()

    fun version(): Long = store.version()

    fun dependenciesUnchanged(snapshot: SnapshotView, dependencies: Set<Long>): Boolean = store.dependenciesUnchanged(snapshot, dependencies)

    fun publishIfCurrent(snapshot: SnapshotView, dependencies: Set<Long>, publish: () -> Unit): Boolean =
        store.publishIfCurrent(snapshot, dependencies, publish)

    fun isChunkAvailable(x: Int, z: Int): Boolean = store.isAvailable(x, z, ::isChunkLoadedChunk)

    fun hasSnapshot(x: Int, z: Int): Boolean = store.hasSnapshot(x, z)

    fun reportMissingSnapshotChunk(chunkX: Int, chunkZ: Int): Boolean = store.reportMissing(chunkX, chunkZ)

    fun getCollisionShape(pos: BlockPos) = geometry.getCollisionShape(pos)

    fun getShapeAabbs(pos: BlockPos) = geometry.getShapeAabbs(pos)

    fun getCollisionHeight(pos: BlockPos) = geometry.getCollisionHeight(pos)

    fun getSupportTopYs(pos: BlockPos) = geometry.getSupportTopYs(pos)

    fun getSupportTopY(pos: BlockPos) = geometry.getSupportTopY(pos)

    fun quantizeFeetOffset(pos: BlockPos, feetY: Double) = geometry.quantizeFeetOffset(pos, feetY)

    fun isBodyClearAt(centerX: Double, feetY: Double, centerZ: Double) = geometry.isBodyClearAt(centerX, feetY, centerZ)

    fun isStandable(pos: BlockPos, feetY: Double) = geometry.isStandable(pos, feetY)

    fun resolveStandingSurface(pos: BlockPos) = geometry.resolveStandingSurface(pos)

    fun getStandableSurfaces(x: Int, z: Int, minFeetY: Double, maxFeetY: Double) = geometry.getStandableSurfaces(x, z, minFeetY, maxFeetY)

    fun isSweepClear(fromX: Double, fromZ: Double, toX: Double, toZ: Double, steps: Int, yAt: (Double) -> Double) =
        geometry.isSweepClear(fromX, fromZ, toX, toZ, steps, yAt)

    fun isPassable(pos: BlockPos): Boolean = getCollisionHeight(pos) == 0.0

    fun isSteppable(pos: BlockPos): Boolean = getCollisionHeight(pos) <= STEP_HEIGHT

    fun isSolid(pos: BlockPos): Boolean = getCollisionHeight(pos) > 0.0

    fun isWalkable(pos: BlockPos): Boolean = isSteppable(pos) && isPassable(pos.above()) && isSolid(pos.below())

    fun clear() {
        store.clear()
        geometry.clear()
    }

    private fun isChunkLoaded(x: Int, z: Int): Boolean = isChunkLoadedChunk(x shr 4, z shr 4)

    private fun isChunkLoadedChunk(cx: Int, cz: Int): Boolean {
        val world = mc.level ?: return false
        return world.chunkSource.getChunk(cx, cz, false) != null
    }

    override fun onChunkEvicted(chunkX: Int, chunkZ: Int) = Unit

    override fun onLoadedChunk(chunk: LevelChunk) {
        if (inDungeons && inBoss) return
        if (inDungeons) store.captureAndStore(chunk) else invalidateChunk(chunk.pos.x, chunk.pos.z)
    }

    override fun onPosEvicted(pos: BlockPos, newState: BlockState) = captureIfDoorChanged(pos, newState)

    override fun onPosApplied(pos: BlockPos, newState: BlockState) = captureIfDoorChanged(pos, newState)

    override fun onAllEvicted() = clear()

    private fun captureIfDoorChanged(pos: BlockPos, newState: BlockState) {
        if (!canCaptureDungeonChunks()) return
        val oldState = store.snapshotState(pos)
        if (oldState == newState || (newState.block !in doorBlocks && oldState?.block !in doorBlocks)) return
        store.captureAndStore(pos.x shr 4, pos.z shr 4)
    }

    private fun canCaptureDungeonChunks(): Boolean = !inDungeons || !inBoss

    private fun invalidateChunk(cx: Int, cz: Int) {
        store.invalidate(cx, cz)
        geometry.invalidateChunk(cx, cz)
    }
}

private val AIR: BlockState = Blocks.AIR.defaultBlockState()
private const val EMPTY_FINGERPRINT = 0L

private fun blockCacheChunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
