package gobby.pathfinder.navigation

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import kotlin.math.abs

internal class RoomLandingIndex(
    private val snapshot: BlockCache.SnapshotView,
    private val deadlineNanos: Long,
    private val maxPerSeed: Int = MAX_PER_SEED,
    private val maxTotal: Int = MAX_TOTAL
) {
    private val access = EtherwarpUtils.cachedAccess(snapshot)
    private val seedCandidates = Long2ObjectOpenHashMap<List<BlockPos>>()
    private val roomCandidates = HashMap<List<Int>, List<BlockPos>>()
    private val mutableCandidate = BlockPos.MutableBlockPos()

    fun query(
        seeds: List<BlockPos>,
        existing: List<BlockPos> = emptyList(),
        totalLimit: Int = maxTotal
    ): List<BlockPos> {
        val result = LinkedHashSet<BlockPos>()
        val distinctSeeds = seeds.distinct()
        distinctSeeds.forEach { appendExact(it, result, totalLimit) }
        existing.forEach { if (result.size < totalLimit && isWarpable(it)) result += it }
        val reserved = minOf(totalLimit / RESERVED_DIVISOR, distinctSeeds.size * maxPerSeed)
        val preparedLimit = (totalLimit - reserved).coerceAtLeast(result.size)
        existing.forEach { if (result.size < preparedLimit && isWarpable(it)) result += it }
        distinctSeeds.forEach { if (result.size < preparedLimit) appendIndexedSeed(it, result, preparedLimit) }
        if (result.size < preparedLimit && System.nanoTime() < deadlineNanos) {
            distinctSeeds.forEach {
                if (result.size < preparedLimit) appendSeed(it, result, preparedLimit)
            }
        }
        return result.toList()
    }

    fun queryExpanded(seeds: List<BlockPos>, existing: List<BlockPos> = emptyList()): List<BlockPos> =
        query(seeds, existing, EXPANDED_MAX_TOTAL)

    fun queryRoom(
        component: Set<Int>,
        grid: Array<MapTile>,
        seeds: List<BlockPos>,
        totalLimit: Int = EXPANDED_MAX_TOTAL
    ): List<BlockPos> {
        if (component.isEmpty()) return emptyList()
        val key = component.sorted()
        val candidates = roomCandidates.getOrPut(key) { cachedRoomScan(key, component, grid) }
        val ordered = candidates.sortedBy { nearestDistanceSq(it, seeds) }
        return query(seeds, ordered, totalLimit)
    }

    fun queryRoomSpread(
        component: Set<Int>,
        grid: Array<MapTile>,
        seeds: List<BlockPos>,
        spacing: Int = SPREAD_SPACING,
        limit: Int = SPREAD_MAX_TOTAL
    ): List<BlockPos> {
        if (component.isEmpty()) return emptyList()
        val key = component.sorted()
        val scanned = roomCandidates.getOrPut(key) { cachedRoomScan(key, component, grid) }
        val spread = scanned.groupBy { bucketKey(it, spacing) }.values.map(List<BlockPos>::first)
        return (queryExact(seeds) + spread).distinct().take(limit)
    }

    private fun bucketKey(position: BlockPos, spacing: Int): Long {
        val x = Math.floorDiv(position.x, spacing).toLong() and COORD_MASK
        val y = Math.floorDiv(position.y, spacing).toLong() and COORD_MASK
        val z = Math.floorDiv(position.z, spacing).toLong() and COORD_MASK
        return (x shl (COORD_BITS * 2)) or (y shl COORD_BITS) or z
    }

    fun queryExact(seeds: List<BlockPos>): List<BlockPos> = buildList {
        seeds.distinct().forEach { seed ->
            if (size >= maxTotal || System.nanoTime() >= deadlineNanos) return@buildList
            if (isWarpable(seed)) add(seed)
        }
    }

    private fun appendExact(seed: BlockPos, result: MutableSet<BlockPos>, limit: Int) {
        if (result.size >= limit || System.nanoTime() >= deadlineNanos) return
        if (isWarpable(seed)) result += seed
    }

    private fun appendIndexedSeed(seed: BlockPos, result: MutableSet<BlockPos>, limit: Int) {
        if (snapshot.canCacheLandingCandidates) SnapshotLandingCandidateCache.get(snapshot, seed)?.let { cached ->
            snapshot.recordDependencies(cached.chunks)
            cached.candidates.forEach { candidate ->
                if (result.size >= limit) return
                result.add(candidate)
            }
            return
        }
        val minChunkX = (seed.x - HORIZONTAL_RADIUS) shr CHUNK_SHIFT
        val maxChunkX = (seed.x + HORIZONTAL_RADIUS) shr CHUNK_SHIFT
        val minChunkZ = (seed.z - HORIZONTAL_RADIUS) shr CHUNK_SHIFT
        val maxChunkZ = (seed.z + HORIZONTAL_RADIUS) shr CHUNK_SHIFT
        val depth = maxChunkZ - minChunkZ + 1
        val total = (maxChunkX - minChunkX + 1) * depth
        val scannedChunks = ArrayList<Long>(total)
        val candidates = ArrayList<BlockPos>()
        for (index in 0 until total) {
            appendChunk(seed, minChunkX + index / depth, minChunkZ + index % depth, candidates, scannedChunks)
        }
        val cacheCandidates = candidatesWithSeed(seed, candidates, seed in result)
        if (snapshot.canCacheLandingCandidates) SnapshotLandingCandidateCache.put(snapshot, seed, cacheCandidates.take(limit), scannedChunks)
        cacheCandidates.forEach { candidate ->
            if (result.size < limit) result.add(candidate)
        }
    }

    private fun candidatesWithSeed(seed: BlockPos, candidates: List<BlockPos>, includeSeed: Boolean): List<BlockPos> =
        if (includeSeed) (listOf(seed) + candidates).distinct() else candidates

    private fun appendChunk(seed: BlockPos, chunkX: Int, chunkZ: Int, result: MutableList<BlockPos>, scannedChunks: MutableList<Long>) {
        val key = chunkKey(chunkX, chunkZ)
        scannedChunks += key
        val candidates = snapshot.nonAirCandidates(key)
        for (candidate in candidates) {
            if (near(seed, candidate) && isWarpable(candidate)) result.add(candidate)
        }
    }

    private fun cachedRoomScan(key: List<Int>, component: Set<Int>, grid: Array<MapTile>): List<BlockPos> {
        if (!snapshot.canCacheLandingCandidates) return scanRoom(component, grid)
        SnapshotLandingCandidateCache.room(snapshot, key)?.let { return it }
        return scanRoom(component, grid).also { SnapshotLandingCandidateCache.putRoom(snapshot, key, it) }
    }

    private fun scanRoom(component: Set<Int>, grid: Array<MapTile>): List<BlockPos> {
        val bounds = RoomBounds.from(component)
        val chunks = ((bounds.minX shr CHUNK_SHIFT)..(bounds.maxX shr CHUNK_SHIFT)).asSequence().flatMap { chunkX ->
            ((bounds.minZ shr CHUNK_SHIFT)..(bounds.maxZ shr CHUNK_SHIFT)).asSequence().map { chunkZ -> chunkKey(chunkX, chunkZ) }
        }
        return chunks.flatMap { snapshot.nonAirCandidates(it).asSequence() }
            .filter { DungeonRooms.containingRoomCell(grid, it.x + CENTER, it.z + CENTER) in component }
            .filter(::isWarpable)
            .distinct()
            .toList()
    }

    private fun nearestDistanceSq(candidate: BlockPos, seeds: List<BlockPos>): Int =
        seeds.minOfOrNull { seed ->
            val dx = candidate.x - seed.x
            val dz = candidate.z - seed.z
            dx * dx + dz * dz
        } ?: 0

    private fun appendSeed(seed: BlockPos, result: MutableSet<BlockPos>, limit: Int): Boolean {
        val key = seed.asLong()
        seedCandidates[key]?.let { candidates ->
            candidates.forEach { if (result.size < limit) result += it }
            return candidates.size >= maxPerSeed
        }
        var accepted = 0
        val candidates = ArrayList<BlockPos>(maxPerSeed)
        for (offset in OFFSETS) {
            if (System.nanoTime() >= deadlineNanos || result.size >= limit) return false
            mutableCandidate.set(seed.x + offset.x, seed.y + offset.y, seed.z + offset.z)
            if (!isWarpable(mutableCandidate)) continue
            val candidate = mutableCandidate.immutable()
            candidates += candidate
            if (result.add(candidate)) accepted++
            if (accepted >= maxPerSeed) {
                seedCandidates[key] = candidates
                return true
            }
        }
        seedCandidates[key] = candidates
        return candidates.size >= maxPerSeed
    }

    private fun isWarpable(position: BlockPos): Boolean {
        return access?.let { EtherwarpUtils.isEtherwarpable(position, it) } == true
    }

    private fun near(seed: BlockPos, candidate: BlockPos): Boolean {
        val deltaX = candidate.x - seed.x
        val deltaZ = candidate.z - seed.z
        return deltaX * deltaX + deltaZ * deltaZ <= HORIZONTAL_RADIUS * HORIZONTAL_RADIUS &&
            abs(candidate.y - seed.y) <= Y_RADIUS
    }

    private companion object {
        private const val MAX_PER_SEED = 4
        private const val MAX_TOTAL = 64
        private const val EXPANDED_MAX_TOTAL = 160
        private const val SPREAD_SPACING = 4
        private const val SPREAD_MAX_TOTAL = 512
        private const val COORD_BITS = 21
        private const val COORD_MASK = 0x1FFFFFL
        private const val RESERVED_DIVISOR = 4
        private const val Y_RADIUS = 3
        private const val HORIZONTAL_RADIUS = 6
        private const val CHUNK_SHIFT = 4
        private const val CHUNK_MASK = 0xFFFFFFFFL
        private const val CENTER = 0.5
        private const val ROOM_MARGIN = 15
        private val OFFSETS = buildOffsets()

        private fun buildOffsets(): List<BlockPos> {
            val side = HORIZONTAL_RADIUS * 2 + 1
            val layers = Y_RADIUS * 2 + 1
            val result = ArrayList<BlockPos>(side * side * layers)
            val total = side * side * layers
            for (index in 0 until total) {
                val horizontal = side * side
                val y = index / horizontal - Y_RADIUS
                val local = index % horizontal
                val x = local / side - HORIZONTAL_RADIUS
                val z = local % side - HORIZONTAL_RADIUS
                result += BlockPos(x, y, z)
            }
            return result.sortedWith(compareBy({ it.x * it.x + it.z * it.z }, { abs(it.y) }, { it.y }, { it.x }, { it.z }))
        }

        private fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and CHUNK_MASK)
    }

    private data class RoomBounds(val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int) {
        companion object {
            fun from(component: Set<Int>): RoomBounds {
                val xs = component.map { MapGrid.worldX(MapGrid.col(it)) }
                val zs = component.map { MapGrid.worldZ(MapGrid.row(it)) }
                return RoomBounds(xs.min() - ROOM_MARGIN, xs.max() + ROOM_MARGIN, zs.min() - ROOM_MARGIN, zs.max() + ROOM_MARGIN)
            }
        }
    }

}
