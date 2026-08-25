package gobby.pathfinder.navmesh

import gobby.pathfinder.world.BlockCache
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

internal object WalkMeshPortalLinker {
    fun linkSameLayer(polys: List<WalkPolygon>) {
        polys.groupBy { Math.round(it.surfaceY * Y_QUANT) }.values.forEach { layer ->
            val cells = boundaryIndex(layer)
            stitchBuckets(cells.values, ::stitchSame)
        }
    }

    fun linkHeightSteps(polys: List<WalkPolygon>) {
        val cells = HashMap<Long, MutableList<WalkPolygon>>()
        polys.forEach { boundaryCells(cells, it) }
        stitchBuckets(cells.values, ::stitchHeight)
    }

    fun linkDiagonalSteps(polys: List<WalkPolygon>) {
        val corners = HashMap<Long, MutableList<WalkPolygon>>()
        polys.forEach { polygon ->
            putCell(corners, polygon.minX, polygon.minZ, polygon)
            putCell(corners, polygon.minX, polygon.maxZ + 1, polygon)
            putCell(corners, polygon.maxX + 1, polygon.minZ, polygon)
            putCell(corners, polygon.maxX + 1, polygon.maxZ + 1, polygon)
        }
        stitchBuckets(corners.values, ::stitchDiagonal)
    }

    private fun boundaryIndex(polys: List<WalkPolygon>): HashMap<Long, MutableList<WalkPolygon>> {
        val cells = HashMap<Long, MutableList<WalkPolygon>>()
        polys.forEach { boundaryCells(cells, it) }
        return cells
    }

    private fun stitchBuckets(
        buckets: Collection<MutableList<WalkPolygon>>,
        stitch: (WalkPolygon, WalkPolygon) -> Unit
    ) {
        val checked = HashSet<Long>()
        buckets.asSequence().flatMap { bucket -> pairSequence(bucket) }.forEach { (first, second) ->
            if (checked.add(pairKey(first.id, second.id))) stitch(first, second)
        }
    }

    private fun pairSequence(bucket: List<WalkPolygon>): Sequence<Pair<WalkPolygon, WalkPolygon>> = sequence {
        val size = bucket.size
        val pairCount = size * (size - PAIR_INDEX_OFFSET) / PAIR_COUNT_DIVISOR
        for (pairIndex in 0 until pairCount) {
            val rowWidth = PAIR_FORMULA_MULTIPLIER * size - PAIR_INDEX_OFFSET
            val first = ((rowWidth - sqrt(rowWidth.toDouble().pow(2) - PAIR_DISCRIMINANT_FACTOR * pairIndex)) / PAIR_FORMULA_DIVISOR).toInt()
            val offset = pairIndex - first * (PAIR_FORMULA_MULTIPLIER * size - first - PAIR_INDEX_OFFSET) / PAIR_COUNT_DIVISOR
            yield(bucket[first] to bucket[first + offset + PAIR_INDEX_OFFSET])
        }
    }

    private fun boundaryCells(cells: HashMap<Long, MutableList<WalkPolygon>>, polygon: WalkPolygon) {
        for (x in polygon.minX - 1..polygon.maxX + 1) {
            putCell(cells, x, polygon.minZ - 1, polygon)
            putCell(cells, x, polygon.maxZ + 1, polygon)
        }
        for (z in polygon.minZ..polygon.maxZ) {
            putCell(cells, polygon.minX - 1, z, polygon)
            putCell(cells, polygon.maxX + 1, z, polygon)
        }
    }

    private fun putCell(cells: HashMap<Long, MutableList<WalkPolygon>>, x: Int, z: Int, polygon: WalkPolygon) {
        cells.getOrPut(columnKey(x, z)) { ArrayList(2) } += polygon
    }

    private fun stitchSame(first: WalkPolygon, second: WalkPolygon) {
        if (abs(first.surfaceY - second.surfaceY) > SAME_LAYER_EPS) return
        seamX(first, second, false) || seamZ(first, second, false)
    }

    private fun seamX(first: WalkPolygon, second: WalkPolygon, heightStep: Boolean): Boolean {
        val (left, right) = when {
            first.maxX + 1 == second.minX -> first to second
            second.maxX + 1 == first.minX -> second to first
            else -> return false
        }
        val minZ = max(left.minZ, right.minZ)
        val maxZ = min(left.maxZ, right.maxZ)
        if (minZ > maxZ) return false
        return addPortal(first, second, Vec3(right.minX.toDouble(), right.surfaceY, minZ.toDouble()), Vec3(right.minX.toDouble(), right.surfaceY, (maxZ + 1).toDouble()), heightStep)
    }

    private fun seamZ(first: WalkPolygon, second: WalkPolygon, heightStep: Boolean): Boolean {
        val (front, back) = when {
            first.maxZ + 1 == second.minZ -> first to second
            second.maxZ + 1 == first.minZ -> second to first
            else -> return false
        }
        val minX = max(front.minX, back.minX)
        val maxX = min(front.maxX, back.maxX)
        if (minX > maxX) return false
        return addPortal(first, second, Vec3(minX.toDouble(), back.surfaceY, back.minZ.toDouble()), Vec3((maxX + 1).toDouble(), back.surfaceY, back.minZ.toDouble()), heightStep)
    }

    private fun addPortal(first: WalkPolygon, second: WalkPolygon, start: Vec3, end: Vec3, heightStep: Boolean): Boolean {
        val portal = WalkPortal(first, second, start, end, heightStep)
        first.portals += portal
        second.portals += portal
        return true
    }

    private fun stitchHeight(first: WalkPolygon, second: WalkPolygon) {
        val delta = abs(first.surfaceY - second.surfaceY)
        if (delta <= SAME_LAYER_EPS || delta > FALL_TOLERANCE) return
        val (low, high) = if (first.surfaceY < second.surfaceY) first to second else second to first
        val isFall = delta > STEP_TOLERANCE
        val clear = if (isFall) WalkMeshCliffLinker.hasFallClearance(high, low) else WalkMeshCliffLinker.hasJumpClearance(low, high)
        if (clear) seamX(low, high, delta > SLAB_STEP_THRESHOLD) || seamZ(low, high, delta > SLAB_STEP_THRESHOLD)
    }

    private fun stitchDiagonal(first: WalkPolygon, second: WalkPolygon) {
        if (first.portals.any { it.opposite(first) === second }) return
        val delta = abs(first.surfaceY - second.surfaceY)
        if (delta > STEP_TOLERANCE) return
        val corner = sharedCorner(first, second) ?: return
        val (cx, cz) = corner
        val (low, high) = if (first.surfaceY < second.surfaceY) first to second else second to first
        if (!diagonalClear(cx, cz, low, high)) return
        addPortal(first, second, Vec3(cx - DIAGONAL_HALF, high.surfaceY, cz - DIAGONAL_HALF), Vec3(cx + DIAGONAL_HALF, high.surfaceY, cz + DIAGONAL_HALF), delta > SLAB_STEP_THRESHOLD)
    }

    private fun sharedCorner(first: WalkPolygon, second: WalkPolygon): Pair<Int, Int>? {
        val candidates = listOf(first.minX to first.minZ, first.minX to first.maxZ + 1, first.maxX + 1 to first.minZ, first.maxX + 1 to first.maxZ + 1)
        return candidates.firstOrNull { (x, z) ->
            (x == second.minX || x == second.maxX + 1) && (z == second.minZ || z == second.maxZ + 1) && !edgeShared(first, second)
        }
    }

    private fun edgeShared(first: WalkPolygon, second: WalkPolygon): Boolean {
        val xTouch = first.maxX + 1 == second.minX || second.maxX + 1 == first.minX
        val zOverlap = max(first.minZ, second.minZ) <= min(first.maxZ, second.maxZ)
        val zTouch = first.maxZ + 1 == second.minZ || second.maxZ + 1 == first.minZ
        val xOverlap = max(first.minX, second.minX) <= min(first.maxX, second.maxX)
        return xTouch && zOverlap || zTouch && xOverlap
    }

    private fun diagonalClear(cx: Int, cz: Int, low: WalkPolygon, high: WalkPolygon): Boolean {
        val cells = listOf(cx - 1 to cz - 1, cx to cz - 1, cx - 1 to cz, cx to cz)
        val lowCell = cells.firstOrNull { low.contains(it.first, it.second) } ?: return false
        val highCell = cells.firstOrNull { high.contains(it.first, it.second) } ?: return false
        if (lowCell == highCell || lowCell.first == highCell.first || lowCell.second == highCell.second) return false
        val feetY = high.surfaceY
        return BlockCache.isBodyClearAt(lowCell.first + 0.5, feetY, highCell.second + 0.5) || BlockCache.isBodyClearAt(highCell.first + 0.5, feetY, lowCell.second + 0.5)
    }

    private fun columnKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun pairKey(first: Int, second: Int): Long {
        val low = minOf(first, second)
        val high = maxOf(first, second)
        return (low.toLong() shl 32) or (high.toLong() and 0xFFFFFFFFL)
    }
    private const val Y_QUANT = 16.0
    private const val SAME_LAYER_EPS = 0.001
    private const val STEP_TOLERANCE = BlockCache.MAX_JUMP_RISE
    private const val FALL_TOLERANCE = 3.0
    private const val SLAB_STEP_THRESHOLD = 0.5625
    private const val DIAGONAL_HALF = 0.25
    private const val PAIR_FORMULA_MULTIPLIER = 2
    private const val PAIR_COUNT_DIVISOR = 2
    private const val PAIR_INDEX_OFFSET = 1
    private const val PAIR_FORMULA_DIVISOR = 2.0
    private const val PAIR_DISCRIMINANT_FACTOR = 8.0
}
