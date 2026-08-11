package gobby.pathfinder.world

import gobby.Gobbyclient.Companion.mc
import gobby.events.util.ChunkScopedCache
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.world.level.block.state.BlockState
import gobby.utils.skyblock.dungeon.map.MapScanner
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.phys.shapes.Shapes
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.roundToInt

object BlockCache : ChunkScopedCache() {
    data class StandSurface(
        val pos: BlockPos,
        val feetY: Double
    )

    internal class ChunkSnapshot(
        val minBlockY: Int,
        val sections: Array<PalettedContainer<BlockState>?>,
        val surfaceHeights: IntArray
    ) {
        fun blockState(x: Int, y: Int, z: Int): BlockState {
            val idx = (y - minBlockY) shr 4
            val container = sections.getOrNull(idx) ?: return AIR
            return container.get(x and 15, y and 15, z and 15)
        }

        fun isSectionAir(y: Int): Boolean {
            val idx = (y - minBlockY) shr 4
            return idx !in sections.indices || sections[idx] == null
        }

        fun sectionFloorY(y: Int): Int = minBlockY + (((y - minBlockY) shr 4) shl 4)

        fun surfaceY(x: Int, z: Int): Int = surfaceHeights[((z and 15) shl 4) or (x and 15)]
    }

    class SnapshotView internal constructor(
        private val snapshots: Map<Long, ChunkSnapshot>,
        private val passableOverrides: Set<Long>
    ) {
        fun hasSnapshot(x: Int, z: Int): Boolean = snapshots.containsKey(chunkKey(x shr 4, z shr 4))

        fun isPassableOverride(pos: BlockPos): Boolean = pos.asLong() in passableOverrides

        fun getBlockState(pos: BlockPos): BlockState {
            if (isPassableOverride(pos)) return AIR
            return snapshots[chunkKey(pos.x shr 4, pos.z shr 4)]?.blockState(pos.x, pos.y, pos.z) ?: AIR
        }
    }

    private val AIR: BlockState = Blocks.AIR.defaultBlockState()

    private val DOOR_BLOCKS = MapScanner.ENTRANCE_DOOR_BLOCKS + setOf(Blocks.COAL_BLOCK, Blocks.DYED_TERRACOTTA.red())

    private val snapshots = ConcurrentHashMap<Long, ChunkSnapshot>()
    private val dirtyChunks = ConcurrentHashMap.newKeySet<Long>()
    private val reportedMissingSnapshots = ConcurrentHashMap.newKeySet<Long>()
    private val reportedDirtySnapshots = ConcurrentHashMap.newKeySet<Long>()
    private val supportTopCache = ConcurrentHashMap<BlockState, List<Double>>()
    private val shapeAabbsCache = ConcurrentHashMap<BlockState, List<AABB>>()
    private data class ColumnRangeKey(val x: Int, val z: Int, val minY: Int, val maxY: Int)
    private val columnSurfacesCache = ConcurrentHashMap<Long, ConcurrentHashMap<ColumnRangeKey, List<StandSurface>>>()

    const val STEP_HEIGHT = 0.6
    const val MAX_JUMP_RISE = 1.25
    const val PLAYER_WIDTH = 0.6
    const val PLAYER_HEIGHT = 1.8
    const val PLAYER_HALF_WIDTH = PLAYER_WIDTH / 2.0

    private const val BODY_EPSILON = 1.0E-3
    private const val SUPPORT_EPSILON = 0.05
    private const val HORIZONTAL_MARGIN = 1.0E-3

    private val passableOverride = ConcurrentHashMap.newKeySet<Long>()

    fun markPassable(pos: BlockPos) {
        passableOverride.add(pos.asLong())
    }

    fun unmarkPassable(pos: BlockPos) {
        passableOverride.remove(pos.asLong())
    }

    fun isPassableOverride(pos: BlockPos): Boolean = pos.asLong() in passableOverride

    fun getBlockState(pos: BlockPos): BlockState {
        if (passableOverride.isNotEmpty() && pos.asLong() in passableOverride) return AIR
        return snapshotFor(pos.x shr 4, pos.z shr 4)?.blockState(pos.x, pos.y, pos.z) ?: AIR
    }

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    fun captureLoadedChunks(minChunkX: Int, minChunkZ: Int, maxChunkX: Int, maxChunkZ: Int, refresh: Boolean = false) {
        val world = mc.level ?: return
        var inspected = 0
        var captured = 0
        (minChunkX..maxChunkX).flatMap { cx -> (minChunkZ..maxChunkZ).map { cz -> cx to cz } }
            .forEach { (cx, cz) ->
                inspected++
                val key = chunkKey(cx, cz)
                if ((refresh || key in dirtyChunks || !snapshots.containsKey(key)) && isChunkLoadedChunk(cx, cz)) {
                    if (captureAndStore(cx, cz) != null) captured++
                }
            }
        CacheDiagnostics.log("capture-range chunks=$inspected captured=$captured refresh=$refresh snapshots=${snapshots.size} dirty=${dirtyChunks.size}")
    }

    private fun snapshotFor(cx: Int, cz: Int): ChunkSnapshot? {
        val key = chunkKey(cx, cz)
        if (key in dirtyChunks) {
            if (reportedDirtySnapshots.add(key)) {
                CacheDiagnostics.log("snapshot-refused-dirty chunk=$cx,$cz loaded=${isChunkLoaded(cx shl 4, cz shl 4)}")
            }
            if (!mc.isSameThread) return null
            return captureAndStore(cx, cz)
        }
        snapshots[key]?.let { return it }
        if (!mc.isSameThread) return null
        return captureAndStore(cx, cz)
    }

    private fun captureAndStore(cx: Int, cz: Int): ChunkSnapshot? {
        val key = chunkKey(cx, cz)
        val world = mc.level ?: return null
        val chunk = world.chunkSource.getChunk(cx, cz, false) ?: return null
        return captureAndStore(chunk)
    }

    fun freeze(): SnapshotView = SnapshotView(HashMap(snapshots), HashSet(passableOverride))

    private fun captureAndStore(chunk: LevelChunk): ChunkSnapshot? {
        val cx = chunk.pos.x
        val cz = chunk.pos.z
        val key = chunkKey(cx, cz)
        if (chunk.isEmpty) {
            CacheDiagnostics.log("capture-skip-empty chunk=$cx,$cz existing=${snapshots.containsKey(key)}")
            return snapshots[key]
        }
        val snapshot = try {
            captureChunk(chunk)
        } catch (e: Exception) {
            CacheDiagnostics.log("capture-failed chunk=$cx,$cz error=${e::class.simpleName}")
            return null
        }
        snapshots[key] = snapshot
        dirtyChunks.remove(key)
        reportedMissingSnapshots.remove(key)
        reportedDirtySnapshots.remove(key)
        columnSurfacesCache.remove(key)
        CacheDiagnostics.log("capture chunk=$cx,$cz sections=${snapshot.sections.count { it != null }} snapshots=${snapshots.size} dirty=${dirtyChunks.size}")
        return snapshot
    }

    private fun captureChunk(chunk: LevelChunk): ChunkSnapshot {
        val liveSections = chunk.sections
        val copied = arrayOfNulls<PalettedContainer<BlockState>>(liveSections.size)
        for (i in liveSections.indices) {
            val section = liveSections[i]
            if (!section.hasOnlyAir()) copied[i] = section.states.copy()
        }
        val surface = IntArray(16 * 16)
        for (lz in 0..15) for (lx in 0..15) {
            surface[(lz shl 4) or lx] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz)
        }
        return ChunkSnapshot(chunk.minY, copied, surface)
    }

    fun isChunkAvailable(x: Int, z: Int): Boolean =
        isChunkLoaded(x, z) || hasSnapshot(x, z)

    fun hasSnapshot(x: Int, z: Int): Boolean =
        chunkKey(x shr 4, z shr 4) !in dirtyChunks && snapshots.containsKey(chunkKey(x shr 4, z shr 4))

    fun debugChunk(x: Int, z: Int): String {
        val cx = x shr 4
        val cz = z shr 4
        val key = chunkKey(cx, cz)
        return "chunk=$cx,$cz loaded=${isChunkLoaded(x, z)} snapshot=${hasSnapshot(x, z)} dirty=${key in dirtyChunks} columns=${columnSurfacesCache[key]?.size ?: 0}"
    }

    fun writeState(reason: String) {
        val lines = mutableListOf(
            "reason=$reason",
            "snapshots=${snapshots.size} dirty=${dirtyChunks.size} columns=${columnSurfacesCache.values.sumOf { it.size }}"
        )
        snapshots.entries.sortedBy { it.key }.forEach { (key, snapshot) ->
            val cx = (key shr 32).toInt()
            val cz = key.toInt()
            lines.add("chunk=$cx,$cz snapshot=${key !in dirtyChunks} dirty=${key in dirtyChunks} sections=${snapshot.sections.count { it != null }} columns=${columnSurfacesCache[key]?.size ?: 0}")
        }
        dirtyChunks.asSequence()
            .filter { !snapshots.containsKey(it) }
            .sorted()
            .forEach { key ->
                lines.add("chunk=${(key shr 32).toInt()},${key.toInt()} snapshot=false dirty=true sections=0 columns=0")
            }
        CacheDiagnostics.writeState(lines)
    }

    fun reportMissingSnapshotChunk(chunkX: Int, chunkZ: Int): Boolean =
        reportedMissingSnapshots.add(chunkKey(chunkX, chunkZ))

    fun getCollisionShape(pos: BlockPos): VoxelShape =
        getBlockState(pos).getCollisionShape(EmptyBlockGetter.INSTANCE, pos, CollisionContext.empty())

    fun getShapeAabbs(pos: BlockPos): List<AABB> {
        val state = getBlockState(pos)
        shapeAabbsCache[state]?.let { return it }
        val shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos, CollisionContext.empty())
        val aabbs = if (shape.isEmpty) emptyList() else shape.toAabbs()
        shapeAabbsCache[state] = aabbs
        return aabbs
    }

    fun getCollisionHeight(pos: BlockPos): Double {
        val shape = getCollisionShape(pos)
        return if (shape.isEmpty) 0.0 else shape.bounds().maxY
    }

    fun getSupportTopYs(pos: BlockPos): List<Double> {
        val state = getBlockState(pos)
        supportTopCache[state]?.let { localTops ->
            return localTops.map { pos.y + it }
        }

        val localTops = getShapeAabbs(pos)
            .asSequence()
            .map { ((it.maxY * 16.0).roundToInt()) / 16.0 }
            .distinct()
            .sortedDescending()
            .toList()

        supportTopCache[state] = localTops
        return localTops.map { pos.y + it }
    }

    fun getSupportTopY(pos: BlockPos): Double? {
        return getSupportTopYs(pos).firstOrNull()
    }

    fun quantizeFeetOffset(pos: BlockPos, feetY: Double): Int {
        return ((feetY - pos.y) * 16.0).roundToInt()
    }

    private fun buildPlayerBox(centerX: Double, feetY: Double, centerZ: Double): AABB {
        return AABB(
            centerX - PLAYER_HALF_WIDTH + HORIZONTAL_MARGIN,
            feetY + BODY_EPSILON,
            centerZ - PLAYER_HALF_WIDTH + HORIZONTAL_MARGIN,
            centerX + PLAYER_HALF_WIDTH - HORIZONTAL_MARGIN,
            feetY + PLAYER_HEIGHT - BODY_EPSILON,
            centerZ + PLAYER_HALF_WIDTH - HORIZONTAL_MARGIN
        )
    }

    private fun hasBlockCollision(box: AABB): Boolean {
        val minX = floor(box.minX).toInt()
        val maxX = floor(box.maxX).toInt()
        val minY = floor(box.minY).toInt()
        val maxY = floor(box.maxY).toInt()
        val minZ = floor(box.minZ).toInt()
        val maxZ = floor(box.maxZ).toInt()
        val cursor = BlockPos.MutableBlockPos()
        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
            for (aabb in getShapeAabbs(cursor.set(x, y, z))) {
                if (aabb.minX + x < box.maxX && aabb.maxX + x > box.minX &&
                    aabb.minY + y < box.maxY && aabb.maxY + y > box.minY &&
                    aabb.minZ + z < box.maxZ && aabb.maxZ + z > box.minZ
                ) return true
            }
        }
        return false
    }

    fun isBodyClearAt(centerX: Double, feetY: Double, centerZ: Double): Boolean {
        return !hasBlockCollision(buildPlayerBox(centerX, feetY, centerZ))
    }

    fun isStandable(pos: BlockPos, feetY: Double): Boolean {
        if (floor(feetY + BODY_EPSILON).toInt() != pos.y) return false
        for ((dx, dz) in BODY_OFFSETS) {
            val cx = pos.x + dx
            val cz = pos.z + dz
            val bodyBox = buildPlayerBox(cx, feetY, cz)
            if (hasBlockCollision(bodyBox)) continue
            val supportBox = AABB(
                bodyBox.minX,
                feetY - SUPPORT_EPSILON,
                bodyBox.minZ,
                bodyBox.maxX,
                feetY + BODY_EPSILON,
                bodyBox.maxZ
            )
            if (hasBlockCollision(supportBox)) return true
        }
        return false
    }

    private val BODY_OFFSETS = listOf(
        0.5 to 0.5,
        0.3 to 0.3, 0.7 to 0.3, 0.3 to 0.7, 0.7 to 0.7
    )

    fun resolveStandingSurface(pos: BlockPos): StandSurface? {
        val candidates = linkedSetOf<Double>()

        getSupportTopYs(pos).forEach { topY ->
            if (topY - pos.y <= STEP_HEIGHT + BODY_EPSILON) {
                candidates.add(topY)
            }
        }
        getSupportTopYs(pos.below()).forEach(candidates::add)

        for (feetY in candidates.sortedDescending()) {
            if (isStandable(pos, feetY)) {
                return StandSurface(pos, feetY)
            }
        }
        return null
    }

    fun isChunkLoaded(x: Int, z: Int): Boolean {
        return isChunkLoadedChunk(x shr 4, z shr 4)
    }

    private fun isChunkLoadedChunk(cx: Int, cz: Int): Boolean {
        val world = mc.level ?: return false
        return world.chunkSource.getChunk(cx, cz, false) != null
    }

    fun getStandableSurfaces(x: Int, z: Int, minFeetY: Double, maxFeetY: Double): List<StandSurface> {
        if (maxFeetY + BODY_EPSILON < minFeetY) return emptyList()
        val snapshot = snapshotFor(x shr 4, z shr 4) ?: return emptyList()

        val minSupportY = floor(minFeetY).toInt() - 1
        var maxSupportY = floor(maxFeetY).toInt() + 1
        val surfaceTop = snapshot.surfaceY(x, z)
        if (surfaceTop < maxSupportY) maxSupportY = surfaceTop
        if (maxSupportY < minSupportY) return emptyList()
        val chunkColumns = columnSurfacesCache.computeIfAbsent(chunkKey(x shr 4, z shr 4)) { ConcurrentHashMap() }
        val cacheKey = ColumnRangeKey(x, z, minSupportY, maxSupportY)
        chunkColumns[cacheKey]?.let { return it }

        val surfaces = mutableListOf<StandSurface>()
        val seen = HashSet<Pair<Long, Int>>()
        var y = maxSupportY
        while (y >= minSupportY) {
            if (snapshot.isSectionAir(y)) {
                y = snapshot.sectionFloorY(y) - 1
                continue
            }
            val supportPos = BlockPos(x, y, z)
            for (topY in getSupportTopYs(supportPos)) {
                if (topY + BODY_EPSILON < minFeetY || topY - BODY_EPSILON > maxFeetY) continue
                val feetPos = BlockPos(x, floor(topY + BODY_EPSILON).toInt(), z)
                if (!isStandable(feetPos, topY)) continue
                val k = feetPos.asLong() to quantizeFeetOffset(feetPos, topY)
                if (seen.add(k)) surfaces.add(StandSurface(feetPos, topY))
            }
            y--
        }
        surfaces.sortByDescending { it.feetY }
        chunkColumns[cacheKey] = surfaces
        return surfaces
    }


    fun isSweepClear(
        fromX: Double,
        fromFeetY: Double,
        fromZ: Double,
        toX: Double,
        toFeetY: Double,
        toZ: Double,
        steps: Int,
        yAt: (Double) -> Double
    ): Boolean {
        if (steps <= 0) return true
        for (i in 0..steps) {
            val t = i.toDouble() / steps.toDouble()
            val x = fromX + (toX - fromX) * t
            val z = fromZ + (toZ - fromZ) * t
            val feetY = yAt(t)
            if (!isBodyClearAt(x, feetY, z)) return false
        }
        return true
    }

    fun isPassable(pos: BlockPos): Boolean = getCollisionHeight(pos) == 0.0

    fun isSteppable(pos: BlockPos): Boolean = getCollisionHeight(pos) <= STEP_HEIGHT

    fun isSolid(pos: BlockPos): Boolean = getCollisionHeight(pos) > 0.0

    fun isWalkable(pos: BlockPos): Boolean {
        val feetClear = isSteppable(pos)
        val headClear = isPassable(pos.above())
        val groundSolid = isSolid(pos.below())
        return feetClear && headClear && groundSolid
    }

    fun clear() {
        snapshots.clear()
        dirtyChunks.clear()
        reportedMissingSnapshots.clear()
        reportedDirtySnapshots.clear()
        supportTopCache.clear()
        shapeAabbsCache.clear()
        columnSurfacesCache.clear()
        passableOverride.clear()
    }

    private fun invalidateChunk(cx: Int, cz: Int) {
        val key = chunkKey(cx, cz)
        if (!dirtyChunks.add(key)) return
        columnSurfacesCache.remove(key)
        reportedMissingSnapshots.remove(key)
        CacheDiagnostics.log("dirty chunk=$cx,$cz snapshots=${snapshots.containsKey(key)}")
    }

    override fun onChunkEvicted(chunkX: Int, chunkZ: Int) = Unit

    override fun onLoadedChunk(chunk: LevelChunk) {
        if (inDungeons) captureAndStore(chunk) else invalidateChunk(chunk.pos.x, chunk.pos.z)
    }

    override fun onPosEvicted(pos: BlockPos, newState: BlockState) {
        val cached = snapshots[chunkKey(pos.x shr 4, pos.z shr 4)]?.blockState(pos.x, pos.y, pos.z) ?: return
        if (cached == newState) return
        if (!isDoorBlock(newState) && !isDoorBlock(cached)) return
        invalidateChunk(pos.x shr 4, pos.z shr 4)
    }

    override fun onPosApplied(pos: BlockPos, newState: BlockState) {
        if (!inDungeons) return
        if (!isDoorBlock(newState) && !isDoorBlock(snapshots[chunkKey(pos.x shr 4, pos.z shr 4)]?.blockState(pos.x, pos.y, pos.z))) return
        invalidateChunk(pos.x shr 4, pos.z shr 4)
    }

    private fun isDoorBlock(state: BlockState?): Boolean = state != null && state.block in DOOR_BLOCKS

    override fun onAllEvicted() {
        clear()
    }
}
