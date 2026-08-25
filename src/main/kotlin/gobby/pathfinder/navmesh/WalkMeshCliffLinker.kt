package gobby.pathfinder.navmesh

import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

internal object WalkMeshCliffLinker {
    fun link(polys: List<WalkPolygon>) {
        val columns = buildColumnIndex(polys)
        val linked = HashSet<Long>()
        polys.forEach { high -> recordExistingPortals(high, linked) }
        val cursor = BlockPos.MutableBlockPos()
        polys.forEach { high -> linkCliffEntries(high, columns, linked, cursor) }
    }

    fun hasJumpClearance(low: WalkPolygon, high: WalkPolygon): Boolean = hasBoundaryClearance(low, high, floor(low.surfaceY).toInt(), floor(high.surfaceY).toInt() + 2)

    fun hasFallClearance(high: WalkPolygon, low: WalkPolygon): Boolean = hasBoundaryClearance(low, high, floor(low.surfaceY).toInt(), floor(high.surfaceY).toInt() + 1)

    private fun hasBoundaryClearance(low: WalkPolygon, high: WalkPolygon, floorY: Int, ceilingY: Int): Boolean =
        boundaryCells(low, high).any { (x, z) -> verticalCorridorClear(x, z, floorY, ceilingY, low.surfaceY, high.surfaceY) }

    private fun recordExistingPortals(high: WalkPolygon, linked: MutableSet<Long>) {
        high.portals.forEach { linked += pairKey(high.id, it.opposite(high).id) }
    }

    private fun linkCliffEntries(
        high: WalkPolygon,
        columns: Map<Long, List<WalkPolygon>>,
        linked: MutableSet<Long>,
        cursor: BlockPos.MutableBlockPos
    ) {
        cliffEntryCells(high).forEach { (x, z) -> linkCliffEntry(high, x, z, columns, linked, cursor) }
    }

    private fun linkCliffEntry(
        high: WalkPolygon,
        x: Int,
        z: Int,
        columns: Map<Long, List<WalkPolygon>>,
        linked: MutableSet<Long>,
        cursor: BlockPos.MutableBlockPos
    ) {
        val landing = columns[columnKey(x, z)]?.firstOrNull { it.surfaceY < high.surfaceY - FALL_TOLERANCE } ?: return
        val pair = pairKey(high.id, landing.id)
        if (pair in linked || high.surfaceY - landing.surfaceY > LONG_FALL_LIMIT) return
        if (!openColumn(x, z, landing.surfaceY, high.surfaceY, cursor)) return
        val edge = cliffEntryEdge(high, x, z, landing.surfaceY)
        val portal = WalkPortal(high, landing, edge.first, edge.second, true)
        high.portals += portal
        landing.portals += portal
        linked += pair
    }

    private fun boundaryCells(first: WalkPolygon, second: WalkPolygon): Sequence<Pair<Int, Int>> = sequence {
        if (first.maxX + 1 == second.minX || second.maxX + 1 == first.minX) {
            val minZ = max(first.minZ, second.minZ)
            val maxZ = minOf(first.maxZ, second.maxZ)
            for (z in minZ..maxZ) yield((if (first.maxX + 1 == second.minX) first.maxX else first.minX) to z)
        }
        if (first.maxZ + 1 == second.minZ || second.maxZ + 1 == first.minZ) {
            val minX = max(first.minX, second.minX)
            val maxX = minOf(first.maxX, second.maxX)
            for (x in minX..maxX) yield(x to (if (first.maxZ + 1 == second.minZ) first.maxZ else first.minZ))
        }
    }

    private fun buildColumnIndex(polys: List<WalkPolygon>): Map<Long, List<WalkPolygon>> {
        val result = HashMap<Long, MutableList<WalkPolygon>>()
        polys.forEach { polygon -> addColumnCells(result, polygon) }
        result.values.forEach { it.sortByDescending(WalkPolygon::surfaceY) }
        return result
    }

    private fun addColumnCells(result: MutableMap<Long, MutableList<WalkPolygon>>, polygon: WalkPolygon) {
        val width = polygon.maxX - polygon.minX + 1
        val depth = polygon.maxZ - polygon.minZ + 1
        for (index in 0 until width * depth) {
            val x = polygon.minX + index / depth
            val z = polygon.minZ + index % depth
            result.getOrPut(columnKey(x, z)) { mutableListOf() } += polygon
        }
    }

    private fun cliffEntryCells(high: WalkPolygon): Sequence<Pair<Int, Int>> = sequence {
        for (z in high.minZ..high.maxZ) {
            yield((high.minX - 1) to z)
            yield((high.maxX + 1) to z)
        }
        for (x in high.minX..high.maxX) {
            yield(x to (high.minZ - 1))
            yield(x to (high.maxZ + 1))
        }
    }

    private fun openColumn(x: Int, z: Int, lowY: Double, highY: Double, cursor: BlockPos.MutableBlockPos): Boolean {
        for (y in floor(lowY).toInt() + 1..floor(highY).toInt()) {
            cursor.set(x, y, z)
            val shape = BlockCache.getCollisionShape(cursor)
            if (!shape.isEmpty && y + shape.max(Direction.Axis.Y) > lowY + GROUND_EPSILON) return false
        }
        return true
    }

    private fun cliffEntryEdge(high: WalkPolygon, x: Int, z: Int, y: Double): Pair<Vec3, Vec3> = when {
        x < high.minX -> Vec3((x + 1).toDouble(), y, z.toDouble()) to Vec3((x + 1).toDouble(), y, (z + 1).toDouble())
        x > high.maxX -> Vec3(x.toDouble(), y, z.toDouble()) to Vec3(x.toDouble(), y, (z + 1).toDouble())
        z < high.minZ -> Vec3(x.toDouble(), y, (z + 1).toDouble()) to Vec3((x + 1).toDouble(), y, (z + 1).toDouble())
        else -> Vec3(x.toDouble(), y, z.toDouble()) to Vec3((x + 1).toDouble(), y, z.toDouble())
    }

    private fun verticalCorridorClear(x: Int, z: Int, floorY: Int, ceilingY: Int, lowY: Double, highY: Double): Boolean {
        val lowSupport = floor(lowY).toInt() - 1
        val highSupport = floor(highY).toInt() - 1
        val bodyCeiling = highY + BlockCache.PLAYER_HEIGHT
        val cursor = BlockPos.MutableBlockPos()
        for (y in floorY..ceilingY) {
            if (y == lowSupport || y == highSupport) continue
            cursor.set(x, y, z)
            val shape = BlockCache.getCollisionShape(cursor)
            if (shape.isEmpty) continue
            val bottom = y + shape.min(Direction.Axis.Y)
            val top = y + shape.max(Direction.Axis.Y)
            if (top <= lowY + GROUND_EPSILON || bottom >= bodyCeiling) continue
            if (abs(top - lowY) >= GROUND_EPSILON && abs(top - highY) >= GROUND_EPSILON) return false
        }
        return true
    }

    private fun pairKey(first: Int, second: Int): Long {
        val low = minOf(first, second)
        val high = maxOf(first, second)
        return (low.toLong() shl 32) or (high.toLong() and 0xFFFFFFFFL)
    }

    private fun columnKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    private const val FALL_TOLERANCE = 3.0
    private const val LONG_FALL_LIMIT = 100.0
    private const val GROUND_EPSILON = 0.01
}
