package gobby.pathfinder.world

import gobby.Gobbyclient.Companion.mc
import gobby.events.util.ChunkScopedCache
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

object BlockCache : ChunkScopedCache() {
    data class StandSurface(
        val pos: BlockPos,
        val feetY: Double
    )

    private val cache = ConcurrentHashMap<Long, BlockState>()
    private val supportTopCache = ConcurrentHashMap<Long, List<Double>>()
    private val scannedChunks = ConcurrentHashMap.newKeySet<Long>()
    private data class ColumnRangeKey(val x: Int, val z: Int, val minY: Int, val maxY: Int)
    private val columnSurfacesCache = ConcurrentHashMap<ColumnRangeKey, List<StandSurface>>()

    const val STEP_HEIGHT = 0.6
    const val MAX_JUMP_RISE = 1.25
    const val PLAYER_WIDTH = 0.6
    const val PLAYER_HEIGHT = 1.8
    const val PLAYER_HALF_WIDTH = PLAYER_WIDTH / 2.0

    private const val CENTER_X = 0.5
    private const val CENTER_Z = 0.5
    private const val BODY_EPSILON = 1.0E-3
    private const val SUPPORT_EPSILON = 0.05
    private const val HORIZONTAL_MARGIN = 1.0E-3

    fun getBlockState(pos: BlockPos): BlockState {
        val key = pos.asLong()
        cache[key]?.let { return it }

        val world = mc.level ?: return Blocks.AIR.defaultBlockState()
        if (!world.hasChunk(pos.x shr 4, pos.z shr 4)) return Blocks.AIR.defaultBlockState()
        val state = world.getBlockState(pos)
        cache[key] = state
        scannedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
        return state
    }

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)

    private fun columnKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    fun isChunkAvailable(x: Int, z: Int): Boolean {
        if (isChunkLoaded(x, z)) return true
        return scannedChunks.contains(chunkKey(x shr 4, z shr 4))
    }

    fun getCollisionShape(pos: BlockPos): VoxelShape {
        val world = mc.level ?: return Shapes.empty()
        return getBlockState(pos).getCollisionShape(world, pos, CollisionContext.empty())
    }

    fun getCollisionHeight(pos: BlockPos): Double {
        val shape = getCollisionShape(pos)
        return if (shape.isEmpty) 0.0 else shape.bounds().maxY
    }

    fun getSupportTopYs(pos: BlockPos): List<Double> {
        val key = pos.asLong()
        supportTopCache[key]?.let { localTops ->
            return localTops.map { pos.y + it }
        }

        val shape = getCollisionShape(pos)
        if (shape.isEmpty) {
            supportTopCache[key] = emptyList()
            return emptyList()
        }

        val localTops = shape.toAabbs()
            .asSequence()
            .map { ((it.maxY * 16.0).roundToInt()) / 16.0 }
            .distinct()
            .sortedDescending()
            .toList()

        supportTopCache[key] = localTops
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
        val world = mc.level ?: return false
        return world.getBlockCollisions(null, box).iterator().hasNext()
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
        val world = mc.level ?: return false
        return world.hasChunk(x shr 4, z shr 4)
    }

    fun getStandableSurfaces(x: Int, z: Int, minFeetY: Double, maxFeetY: Double): List<StandSurface> {
        if (maxFeetY + BODY_EPSILON < minFeetY) return emptyList()
        if (!isChunkAvailable(x, z)) return emptyList()

        val minSupportY = floor(minFeetY).toInt() - 1
        var maxSupportY = floor(maxFeetY).toInt() + 1
        val world = mc.level
        val chunk: LevelChunk? = world?.getChunk(x shr 4, z shr 4)
        if (chunk != null) {
            val top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x and 15, z and 15)
            if (top < maxSupportY) maxSupportY = top
        }
        if (maxSupportY < minSupportY) return emptyList()
        val cacheKey = ColumnRangeKey(x, z, minSupportY, maxSupportY)
        columnSurfacesCache[cacheKey]?.let { return it }

        val surfaces = mutableListOf<StandSurface>()
        val seen = HashSet<Pair<Long, Int>>()
        val sections = chunk?.sections
        val chunkMinY = world?.minY ?: 0
        var y = maxSupportY
        while (y >= minSupportY) {
            val sectionIdx = (y - chunkMinY) shr 4
            if (sections != null && sectionIdx in sections.indices && sections[sectionIdx].hasOnlyAir()) {
                y = (chunkMinY + (sectionIdx shl 4)) - 1
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
        columnSurfacesCache[cacheKey] = surfaces
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
        cache.clear()
        supportTopCache.clear()
        scannedChunks.clear()
        columnSurfacesCache.clear()
    }

    fun invalidate(pos: BlockPos) {
        cache.remove(pos.asLong())
        supportTopCache.remove(pos.asLong())
        columnSurfacesCache.keys.removeIf { it.x == pos.x && it.z == pos.z }
    }

    override fun onChunkEvicted(chunkX: Int, chunkZ: Int) {
        // Intentionally a no-op because BlockCache is a long lived cache that should survive
        // chunk unload so revisiting the same area doesn't re-fetch every BlockState.
        // World level invalidation happens via onAllEvicted (world load) and per block
        // changes via onPosEvicted
    }

    override fun onPosEvicted(pos: BlockPos, newState: BlockState) {
        invalidate(pos)
    }

    override fun onAllEvicted() {
        clear()
    }
}
