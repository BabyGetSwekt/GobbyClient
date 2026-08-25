package gobby.pathfinder.world

import gobby.utils.BlockBox
import net.minecraft.core.BlockPos
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.roundToInt

internal class BlockCacheGeometry(
    private val stateAt: (BlockPos) -> BlockState,
    private val snapshotAt: (Int, Int) -> BlockCache.ChunkSnapshot?
) {
    private val feetQuantization = 16.0
    private val bodyEpsilon = 1.0E-3
    private val supportEpsilon = 0.05
    private val horizontalMargin = 1.0E-3
    private val playerWidth = 0.6
    private val playerHeight = 1.8
    private val playerHalfWidth = playerWidth / 2.0

    private data class ColumnRangeKey(val x: Int, val z: Int, val minFeet: Int, val maxFeet: Int)

    private val supportTopCache = ConcurrentHashMap<BlockState, List<Double>>()
    private val shapeAabbsCache = ConcurrentHashMap<BlockState, List<AABB>>()
    private val columnSurfacesCache = ConcurrentHashMap<Long, ConcurrentHashMap<ColumnRangeKey, List<BlockCache.StandSurface>>>()
    private val bodyOffsets = listOf(0.5 to 0.5, 0.3 to 0.3, 0.7 to 0.3, 0.3 to 0.7, 0.7 to 0.7)

    fun getCollisionShape(pos: BlockPos): VoxelShape =
        stateAt(pos).getCollisionShape(EmptyBlockGetter.INSTANCE, pos, CollisionContext.empty())

    fun getShapeAabbs(pos: BlockPos): List<AABB> {
        val state = stateAt(pos)
        shapeAabbsCache[state]?.let { return it }
        val shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos, CollisionContext.empty())
        val aabbs = if (shape.isEmpty) emptyList() else shape.toAabbs()
        shapeAabbsCache[state] = aabbs
        return aabbs
    }

    fun getCollisionHeight(pos: BlockPos): Double = getCollisionShape(pos).let { if (it.isEmpty) 0.0 else it.bounds().maxY }

    fun getSupportTopYs(pos: BlockPos): List<Double> {
        val state = stateAt(pos)
        supportTopCache[state]?.let { tops -> return tops.map { pos.y + it } }
        val tops = getShapeAabbs(pos).asSequence()
            .map { (it.maxY * feetQuantization).roundToInt() / feetQuantization }
            .distinct()
            .sortedDescending()
            .toList()
        supportTopCache[state] = tops
        return tops.map { pos.y + it }
    }

    fun getSupportTopY(pos: BlockPos): Double? = getSupportTopYs(pos).firstOrNull()

    fun quantizeFeetOffset(pos: BlockPos, feetY: Double): Int = ((feetY - pos.y) * feetQuantization).roundToInt()

    fun isBodyClearAt(centerX: Double, feetY: Double, centerZ: Double): Boolean = !hasBlockCollision(buildPlayerBox(centerX, feetY, centerZ))

    fun isStandable(pos: BlockPos, feetY: Double): Boolean {
        if (floor(feetY + bodyEpsilon).toInt() != pos.y) return false
        return bodyOffsets.any { (dx, dz) ->
            val bodyBox = buildPlayerBox(pos.x + dx, feetY, pos.z + dz)
            !hasBlockCollision(bodyBox) && hasBlockCollision(
                AABB(bodyBox.minX, feetY - supportEpsilon, bodyBox.minZ, bodyBox.maxX, feetY + bodyEpsilon, bodyBox.maxZ)
            )
        }
    }

    fun resolveStandingSurface(pos: BlockPos): BlockCache.StandSurface? {
        val candidates = (getSupportTopYs(pos) + getSupportTopYs(pos.below()))
            .filter { it - pos.y <= 0.6 + bodyEpsilon }
            .distinct()
            .sortedDescending()
        return candidates.firstOrNull { isStandable(pos, it) }?.let { BlockCache.StandSurface(pos, it) }
    }

    fun getStandableSurfaces(x: Int, z: Int, minFeetY: Double, maxFeetY: Double): List<BlockCache.StandSurface> {
        if (maxFeetY + bodyEpsilon < minFeetY) return emptyList()
        val snapshot = snapshotAt(x shr 4, z shr 4) ?: return emptyList()
        val minSupportY = floor(minFeetY).toInt() - 1
        val maxSupportY = minOf(floor(maxFeetY).toInt() + 1, snapshot.surfaceY(x, z))
        if (maxSupportY < minSupportY) return emptyList()
        val key = chunkKey(x shr 4, z shr 4)
        val cache = columnSurfacesCache.computeIfAbsent(key) { ConcurrentHashMap() }
        val rangeKey = ColumnRangeKey(x, z, (minFeetY * feetQuantization).roundToInt(), (maxFeetY * feetQuantization).roundToInt())
        cache[rangeKey]?.let { return it }
        val surfaces = mutableListOf<BlockCache.StandSurface>()
        val seen = HashSet<Pair<Long, Int>>()
        var y = maxSupportY
        while (y >= minSupportY) {
            if (snapshot.isSectionAir(y)) {
                y = snapshot.sectionFloorY(y) - 1
                continue
            }
            getSupportTopYs(BlockPos(x, y, z)).forEach { topY ->
                if (topY + bodyEpsilon >= minFeetY && topY - bodyEpsilon <= maxFeetY) {
                    val feet = BlockPos(x, floor(topY + bodyEpsilon).toInt(), z)
                    val id = feet.asLong() to quantizeFeetOffset(feet, topY)
                    if (isStandable(feet, topY) && seen.add(id)) surfaces.add(BlockCache.StandSurface(feet, topY))
                }
            }
            y--
        }
        surfaces.sortByDescending { it.feetY }
        cache[rangeKey] = surfaces
        return surfaces
    }

    fun isSweepClear(fromX: Double, fromZ: Double, toX: Double, toZ: Double, steps: Int, yAt: (Double) -> Double): Boolean {
        if (steps <= 0) return true
        return (0..steps).none { i ->
            val t = i.toDouble() / steps
            hasBlockCollision(buildPlayerBox(fromX + (toX - fromX) * t, yAt(t), fromZ + (toZ - fromZ) * t))
        }
    }

    fun clear() {
        supportTopCache.clear()
        shapeAabbsCache.clear()
        columnSurfacesCache.clear()
    }

    fun invalidateChunk(cx: Int, cz: Int) {
        columnSurfacesCache.remove(chunkKey(cx, cz))
    }

    private fun buildPlayerBox(centerX: Double, feetY: Double, centerZ: Double): AABB = AABB(
        centerX - playerHalfWidth + horizontalMargin,
        feetY + bodyEpsilon,
        centerZ - playerHalfWidth + horizontalMargin,
        centerX + playerHalfWidth - horizontalMargin,
        feetY + playerHeight - bodyEpsilon,
        centerZ + playerHalfWidth - horizontalMargin
    )

    private fun hasBlockCollision(box: AABB): Boolean {
        val bounds = BlockBox.covering(box)
        val cursor = BlockPos.MutableBlockPos()
        for (index in 0 until bounds.cellCount) {
            val position = cursor.set(bounds.xAt(index), bounds.yAt(index), bounds.zAt(index)).immutable()
            if (getShapeAabbs(position).any { aabb -> overlaps(aabb, position, box) }) return true
        }
        return false
    }

    private fun overlaps(aabb: AABB, pos: BlockPos, box: AABB): Boolean =
        aabb.minX + pos.x < box.maxX && aabb.maxX + pos.x > box.minX &&
            aabb.minY + pos.y < box.maxY && aabb.maxY + pos.y > box.minY &&
            aabb.minZ + pos.z < box.maxZ && aabb.maxZ + pos.z > box.minZ

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) or (cz.toLong() and 0xFFFFFFFFL)
}
