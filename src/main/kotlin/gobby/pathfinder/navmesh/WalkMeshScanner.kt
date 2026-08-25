package gobby.pathfinder.navmesh

import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ForkJoinPool
import java.util.stream.IntStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object WalkMeshScanner {

    private const val DEFAULT_PAD = 24
    private const val MAX_PAD = 128
    private const val MAX_RECT_EXTENT = 6
    private const val Y_QUANT = 16.0
    private const val Y_BLEED = 20
    private const val STEP_TOLERANCE = BlockCache.MAX_JUMP_RISE
    private const val FALL_TOLERANCE = 3.0
    private const val SAME_LAYER_EPS = 0.001
    private const val SLAB_STEP_THRESHOLD = 0.5625
    private const val DIAGONAL_PORTAL_HALF = 0.25
    private const val CLEARANCE_CAP = 4
    private const val GROUND_EPSILON = 0.01
    private const val LONG_FALL_LIMIT = 100.0

    private val backgroundPool: ForkJoinPool by lazy {
        val workers = max(1, (Runtime.getRuntime().availableProcessors() - 1) / 2).coerceAtMost(4)
        ForkJoinPool(workers, { pool ->
            val w = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool)
            w.isDaemon = true
            w.priority = Thread.NORM_PRIORITY - 2
            w.name = "GobbyMeshScan-${w.poolIndex}"
            w
        }, null, false)
    }

    private fun <T> parallel(block: () -> T): T = backgroundPool.submit<T>(block).get()

    fun scan(start: Vec3, goal: Vec3, scanRange: Int): WalkMesh {
        WalkPolygon.resetIds()

        val verticalSpread = abs(start.y - goal.y).toInt()
        val horizontalSpread = max(abs(start.x - goal.x), abs(start.z - goal.z)).toInt()
        val pad = max(DEFAULT_PAD, (max(verticalSpread, horizontalSpread) * 3) / 2).coerceAtMost(MAX_PAD).coerceAtMost(scanRange)

        val box = computeScanBox(start, goal, pad, scanRange)
        val byLayer = harvestSurfaces(box)
        if (byLayer.isEmpty()) return WalkMesh(emptyList())

        val polygons = parallel {
            byLayer.entries.parallelStream().flatMap { entry ->
                val surfaces = entry.value
                val layerY = surfaces.first().y
                mergeIntoRectangles(box, surfaces.toBitGrid(box), layerY).stream()
            }.toList()
        }

        if (polygons.isEmpty()) return WalkMesh(emptyList())

        annotateClearance(polygons, byLayer, box)
        WalkMeshPortalLinker.linkSameLayer(polygons)
        WalkMeshPortalLinker.linkHeightSteps(polygons)
        WalkMeshPortalLinker.linkDiagonalSteps(polygons)
        WalkMeshCliffLinker.link(polygons)

        return WalkMesh(polygons)
    }

    private data class ScanBox(
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int
    ) {
        val sizeX: Int get() = maxX - minX + 1
        val sizeZ: Int get() = maxZ - minZ + 1
    }

    private data class Surface(val gx: Int, val gz: Int, val y: Double)

    private fun computeScanBox(start: Vec3, goal: Vec3, pad: Int, range: Int): ScanBox {
        var lowX = floor(min(start.x, goal.x)).toInt() - pad
        var highX = ceil(max(start.x, goal.x)).toInt() + pad
        var lowZ = floor(min(start.z, goal.z)).toInt() - pad
        var highZ = ceil(max(start.z, goal.z)).toInt() + pad
        val lowY = floor(min(start.y, goal.y)).toInt() - Y_BLEED
        val highY = ceil(max(start.y, goal.y)).toInt() + Y_BLEED

        val cx = (lowX + highX) / 2
        val cz = (lowZ + highZ) / 2
        lowX = max(lowX, cx - range)
        highX = min(highX, cx + range)
        lowZ = max(lowZ, cz - range)
        highZ = min(highZ, cz + range)

        return ScanBox(lowX, lowY, lowZ, highX, highY, highZ)
    }

    private fun harvestSurfaces(box: ScanBox): Map<Long, List<Surface>> {
        val cellResults = ConcurrentHashMap<Long, MutableList<Surface>>()
        val totalCells = box.sizeX * box.sizeZ
        parallel { IntStream.range(0, totalCells).parallel().forEach { idx ->
            val gx = idx / box.sizeZ
            val gz = idx % box.sizeZ
            val wx = box.minX + gx
            val wz = box.minZ + gz
            val surfaces = BlockCache.getStandableSurfaces(
                wx, wz,
                box.minY.toDouble(),
                box.maxY.toDouble() + 1.0
            )
            recordSurfaces(cellResults, surfaces, gx, gz)
        } }
        return cellResults
    }

    private fun recordSurfaces(cellResults: ConcurrentHashMap<Long, MutableList<Surface>>, surfaces: List<BlockCache.StandSurface>, gx: Int, gz: Int) {
        surfaces.forEach { surface ->
            val key = Math.round(surface.feetY * Y_QUANT)
            cellResults.computeIfAbsent(key) { Collections.synchronizedList(ArrayList()) } += Surface(gx, gz, surface.feetY)
        }
    }

    private fun List<Surface>.toBitGrid(box: ScanBox): Array<BooleanArray> {
        val grid = Array(box.sizeX) { BooleanArray(box.sizeZ) }
        for (s in this) grid[s.gx][s.gz] = true
        return grid
    }

    private fun mergeIntoRectangles(box: ScanBox, grid: Array<BooleanArray>, layerY: Double): List<WalkPolygon> {
        val used = Array(box.sizeX) { BooleanArray(box.sizeZ) }
        val rectangles = ArrayList<WalkPolygon>()

        for (index in 0 until box.sizeX * box.sizeZ) {
            val gx = index / box.sizeZ
            val gz = index % box.sizeZ
            if (!grid[gx][gz] || used[gx][gz]) continue

            val extentX = growRectangleWidth(box, grid, used, gx, gz)
            val extentZ = growRectangleDepth(box, grid, used, gx, extentX, gz)

            markUsed(used, gx, extentX, gz, extentZ)

            rectangles += WalkPolygon(
                box.minX + gx,
                box.minZ + gz,
                box.minX + extentX,
                box.minZ + extentZ,
                layerY
            )
        }
        return rectangles
    }

    private fun growRectangleWidth(box: ScanBox, grid: Array<BooleanArray>, used: Array<BooleanArray>, startX: Int, z: Int): Int {
        var endX = startX
        while (endX + 1 < box.sizeX && endX - startX + 1 < MAX_RECT_EXTENT && grid[endX + 1][z] && !used[endX + 1][z]) endX++
        return endX
    }

    private fun growRectangleDepth(box: ScanBox, grid: Array<BooleanArray>, used: Array<BooleanArray>, minX: Int, maxX: Int, startZ: Int): Int {
        var endZ = startZ
        while (endZ + 1 < box.sizeZ && endZ - startZ + 1 < MAX_RECT_EXTENT && rowAvailable(grid, used, minX, maxX, endZ + 1)) endZ++
        return endZ
    }

    private fun rowAvailable(grid: Array<BooleanArray>, used: Array<BooleanArray>, minX: Int, maxX: Int, z: Int): Boolean =
        (minX..maxX).all { x -> grid[x][z] && !used[x][z] }

    private fun markUsed(used: Array<BooleanArray>, minX: Int, maxX: Int, minZ: Int, maxZ: Int) {
        for (index in 0..(maxX - minX) * (maxZ - minZ)) {
            val width = maxX - minX + 1
            used[minX + index % width][minZ + index / width] = true
        }
    }

    private fun annotateClearance(polys: List<WalkPolygon>, byLayer: Map<Long, List<Surface>>, box: ScanBox) {
        val distMaps = ConcurrentHashMap<Long, Array<IntArray>>()
        parallel {
            byLayer.entries.parallelStream().forEach { entry ->
                val walkable = Array(box.sizeX) { BooleanArray(box.sizeZ) }
                markWalkable(walkable, entry.value)
                distMaps[entry.key] = bfsDistanceTransform(walkable, box.sizeX, box.sizeZ)
            }
        }
        parallel { polys.parallelStream().forEach { poly ->
            val key = Math.round(poly.surfaceY * Y_QUANT)
            val map = distMaps[key] ?: return@forEach
            poly.wallClearance = lowestClearance(poly, box, map)
        } }
    }

    private fun markWalkable(walkable: Array<BooleanArray>, surfaces: List<Surface>) {
        surfaces.forEach { surface -> walkable[surface.gx][surface.gz] = true }
    }

    private fun lowestClearance(poly: WalkPolygon, box: ScanBox, map: Array<IntArray>): Int {
        var lowest = CLEARANCE_CAP
        val width = poly.maxX - poly.minX + 1
        val area = (poly.maxX - poly.minX) * (poly.maxZ - poly.minZ)
        for (index in 0..area) {
            val gx = poly.minX + index % width - box.minX
            val gz = poly.minZ + index / width - box.minZ
            if (gx in 0 until box.sizeX && gz in 0 until box.sizeZ) lowest = min(lowest, map[gx][gz])
        }
        return lowest
    }

    private fun bfsDistanceTransform(walkable: Array<BooleanArray>, sx: Int, sz: Int): Array<IntArray> {
        val dist = Array(sx) { IntArray(sz) { CLEARANCE_CAP } }
        val queue = ArrayDeque<IntArray>()
        for (index in 0 until sx * sz) {
            val x = index / sz
            val z = index % sz
            val edge = x == 0 || x == sx - 1 || z == 0 || z == sz - 1
            if (!walkable[x][z] || edge) {
                dist[x][z] = 0
                queue.addLast(intArrayOf(x, z))
            }
        }
        val faces = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val here = dist[cell[0]][cell[1]]
            if (here >= CLEARANCE_CAP) continue
            relaxNeighbors(cell, here, faces, dist, queue, sx, sz)
        }
        return dist
    }

    private fun relaxNeighbors(cell: IntArray, here: Int, faces: Array<IntArray>, dist: Array<IntArray>, queue: ArrayDeque<IntArray>, sx: Int, sz: Int) {
        faces.forEach { direction ->
            val nx = cell[0] + direction[0]
            val nz = cell[1] + direction[1]
            if (nx !in 0 until sx || nz !in 0 until sz || dist[nx][nz] <= here + 1) return@forEach
            dist[nx][nz] = here + 1
            queue.addLast(intArrayOf(nx, nz))
        }
    }

}
