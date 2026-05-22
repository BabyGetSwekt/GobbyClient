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
        linkSameLayerPortals(polygons)
        linkHeightStepPortals(polygons)
        linkDiagonalStepPortals(polygons)
        linkCliffFallPortals(polygons)

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
            for (s in surfaces) {
                val key = Math.round(s.feetY * Y_QUANT)
                cellResults.computeIfAbsent(key) { Collections.synchronizedList(ArrayList()) } += Surface(gx, gz, s.feetY)
            }
        } }
        return cellResults
    }

    private fun List<Surface>.toBitGrid(box: ScanBox): Array<BooleanArray> {
        val grid = Array(box.sizeX) { BooleanArray(box.sizeZ) }
        for (s in this) grid[s.gx][s.gz] = true
        return grid
    }

    private fun mergeIntoRectangles(
        box: ScanBox,
        grid: Array<BooleanArray>,
        layerY: Double
    ): List<WalkPolygon> {
        val used = Array(box.sizeX) { BooleanArray(box.sizeZ) }
        val rectangles = ArrayList<WalkPolygon>()

        for (gx in 0 until box.sizeX) for (gz in 0 until box.sizeZ) {
            if (!grid[gx][gz] || used[gx][gz]) continue

            var extentX = gx
            while (extentX + 1 < box.sizeX &&
                (extentX - gx + 1) < MAX_RECT_EXTENT &&
                grid[extentX + 1][gz] &&
                !used[extentX + 1][gz]
            ) extentX++

            var extentZ = gz
            var canGrowZ = true
            while (canGrowZ && extentZ + 1 < box.sizeZ && (extentZ - gz + 1) < MAX_RECT_EXTENT) {
                for (cx in gx..extentX) {
                    if (!grid[cx][extentZ + 1] || used[cx][extentZ + 1]) {
                        canGrowZ = false
                        break
                    }
                }
                if (canGrowZ) extentZ++
            }

            for (cx in gx..extentX) for (cz in gz..extentZ) used[cx][cz] = true

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

    private fun annotateClearance(
        polys: List<WalkPolygon>,
        byLayer: Map<Long, List<Surface>>,
        box: ScanBox
    ) {
        val distMaps = ConcurrentHashMap<Long, Array<IntArray>>()
        parallel {
            byLayer.entries.parallelStream().forEach { entry ->
                val walkable = Array(box.sizeX) { BooleanArray(box.sizeZ) }
                for (s in entry.value) walkable[s.gx][s.gz] = true
                distMaps[entry.key] = bfsDistanceTransform(walkable, box.sizeX, box.sizeZ)
            }
        }
        parallel { polys.parallelStream().forEach { poly ->
            val key = Math.round(poly.surfaceY * Y_QUANT)
            val map = distMaps[key] ?: return@forEach
            var lowest = CLEARANCE_CAP
            for (wx in poly.minX..poly.maxX) for (wz in poly.minZ..poly.maxZ) {
                val gx = wx - box.minX
                val gz = wz - box.minZ
                if (gx in 0 until box.sizeX && gz in 0 until box.sizeZ) {
                    lowest = min(lowest, map[gx][gz])
                }
            }
            poly.wallClearance = lowest
        } }
    }

    private fun bfsDistanceTransform(walkable: Array<BooleanArray>, sx: Int, sz: Int): Array<IntArray> {
        val dist = Array(sx) { IntArray(sz) { CLEARANCE_CAP } }
        val queue = ArrayDeque<IntArray>()
        for (x in 0 until sx) for (z in 0 until sz) {
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
            for (d in faces) {
                val nx = cell[0] + d[0]; val nz = cell[1] + d[1]
                if (nx !in 0 until sx || nz !in 0 until sz) continue
                if (dist[nx][nz] > here + 1) {
                    dist[nx][nz] = here + 1
                    queue.addLast(intArrayOf(nx, nz))
                }
            }
        }
        return dist
    }

    private fun linkSameLayerPortals(polys: List<WalkPolygon>) {
        val byLayer = polys.groupBy { Math.round(it.surfaceY * Y_QUANT) }
        for (layerPolys in byLayer.values) {
            val cellMap = HashMap<Long, MutableList<WalkPolygon>>()
            for (p in layerPolys) {
                for (x in p.minX - 1..p.maxX + 1) {
                    putCell(cellMap, x, p.minZ - 1, p)
                    putCell(cellMap, x, p.maxZ + 1, p)
                }
                for (z in p.minZ..p.maxZ) {
                    putCell(cellMap, p.minX - 1, z, p)
                    putCell(cellMap, p.maxX + 1, z, p)
                }
            }
            val checked = HashSet<Long>()
            for (bucket in cellMap.values) {
                for (i in 0 until bucket.size) for (j in i + 1 until bucket.size) {
                    val a = bucket[i]; val b = bucket[j]
                    val key = pairKey(a.id, b.id)
                    if (checked.add(key)) tryStitchSameLayer(a, b)
                }
            }
        }
    }

    private fun putCell(map: HashMap<Long, MutableList<WalkPolygon>>, x: Int, z: Int, poly: WalkPolygon) {
        val key = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
        map.getOrPut(key) { ArrayList(2) } += poly
    }

    private fun pairKey(a: Int, b: Int): Long {
        val lo = if (a < b) a else b
        val hi = if (a < b) b else a
        return (lo.toLong() shl 32) or (hi.toLong() and 0xFFFFFFFFL)
    }

    private fun tryStitchSameLayer(a: WalkPolygon, b: WalkPolygon) {
        if (abs(a.surfaceY - b.surfaceY) > SAME_LAYER_EPS) return
        seamX(a, b, false) || seamZ(a, b, false)
    }

    private fun seamX(a: WalkPolygon, b: WalkPolygon, heightStep: Boolean): Boolean {
        val (left, right) = when {
            a.maxX + 1 == b.minX -> a to b
            b.maxX + 1 == a.minX -> b to a
            else -> return false
        }
        val overlapMinZ = max(left.minZ, right.minZ)
        val overlapMaxZ = min(left.maxZ, right.maxZ)
        if (overlapMinZ > overlapMaxZ) return false

        val edgeX = right.minX.toDouble()
        val y = right.surfaceY
        val p1 = Vec3(edgeX, y, overlapMinZ.toDouble())
        val p2 = Vec3(edgeX, y, (overlapMaxZ + 1).toDouble())
        val portal = WalkPortal(a, b, p1, p2, heightStep)
        a.portals += portal
        b.portals += portal
        return true
    }

    private fun seamZ(a: WalkPolygon, b: WalkPolygon, heightStep: Boolean): Boolean {
        val (front, back) = when {
            a.maxZ + 1 == b.minZ -> a to b
            b.maxZ + 1 == a.minZ -> b to a
            else -> return false
        }
        val overlapMinX = max(front.minX, back.minX)
        val overlapMaxX = min(front.maxX, back.maxX)
        if (overlapMinX > overlapMaxX) return false

        val edgeZ = back.minZ.toDouble()
        val y = back.surfaceY
        val p1 = Vec3(overlapMinX.toDouble(), y, edgeZ)
        val p2 = Vec3((overlapMaxX + 1).toDouble(), y, edgeZ)
        val portal = WalkPortal(a, b, p1, p2, heightStep)
        a.portals += portal
        b.portals += portal
        return true
    }

    private fun linkHeightStepPortals(polys: List<WalkPolygon>) {
        val spatialHash = HashMap<Long, MutableList<WalkPolygon>>()
        for (p in polys) {
            for (x in p.minX - 1..p.maxX + 1) {
                putCell(spatialHash, x, p.minZ - 1, p)
                putCell(spatialHash, x, p.maxZ + 1, p)
            }
            for (z in p.minZ..p.maxZ) {
                putCell(spatialHash, p.minX - 1, z, p)
                putCell(spatialHash, p.maxX + 1, z, p)
            }
        }
        val checked = HashSet<Long>()
        for (bucket in spatialHash.values) {
            for (i in 0 until bucket.size) for (j in i + 1 until bucket.size) {
                val a = bucket[i]; val b = bucket[j]
                val pairId = pairKey(a.id, b.id)
                if (!checked.add(pairId)) continue
                tryStitchHeightStep(a, b)
            }
        }
    }

    private fun tryStitchHeightStep(a: WalkPolygon, b: WalkPolygon) {
        val delta = abs(a.surfaceY - b.surfaceY)
        if (delta <= SAME_LAYER_EPS) return
        if (delta > FALL_TOLERANCE) return
        val (low, high) = if (a.surfaceY < b.surfaceY) a to b else b to a
        val heightStep = delta > SLAB_STEP_THRESHOLD
        val isFall = delta > STEP_TOLERANCE
        val passes = if (isFall) hasFallClearance(high, low) else hasJumpClearance(low, high)
        if (!passes) return
        seamX(low, high, heightStep) || seamZ(low, high, heightStep)
    }

    private fun linkDiagonalStepPortals(polys: List<WalkPolygon>) {
        val cornerBuckets = HashMap<Long, MutableList<WalkPolygon>>()
        for (p in polys) {
            putCell(cornerBuckets, p.minX, p.minZ, p)
            putCell(cornerBuckets, p.minX, p.maxZ + 1, p)
            putCell(cornerBuckets, p.maxX + 1, p.minZ, p)
            putCell(cornerBuckets, p.maxX + 1, p.maxZ + 1, p)
        }
        val checked = HashSet<Long>()
        for (bucket in cornerBuckets.values) {
            for (i in 0 until bucket.size) for (j in i + 1 until bucket.size) {
                val a = bucket[i]; val b = bucket[j]
                val pairId = pairKey(a.id, b.id)
                if (!checked.add(pairId)) continue
                tryStitchDiagonalStep(a, b)
            }
        }
    }

    private fun tryStitchDiagonalStep(a: WalkPolygon, b: WalkPolygon) {
        if (a.portals.any { it.opposite(a) === b }) return
        val delta = abs(a.surfaceY - b.surfaceY)
        if (delta > STEP_TOLERANCE) return
        val corner = sharedCornerPoint(a, b) ?: return
        val (cx, cz) = corner
        val (low, high) = if (a.surfaceY < b.surfaceY) a to b else b to a
        if (!diagonalCornerClear(cx, cz, low, high)) return
        val y = high.surfaceY
        val p1 = Vec3(cx.toDouble() - DIAGONAL_PORTAL_HALF, y, cz.toDouble() - DIAGONAL_PORTAL_HALF)
        val p2 = Vec3(cx.toDouble() + DIAGONAL_PORTAL_HALF, y, cz.toDouble() + DIAGONAL_PORTAL_HALF)
        val heightStep = delta > SLAB_STEP_THRESHOLD
        val portal = WalkPortal(a, b, p1, p2, heightStep)
        a.portals += portal
        b.portals += portal
    }

    private fun sharedCornerPoint(a: WalkPolygon, b: WalkPolygon): Pair<Int, Int>? {
        val candidates = listOf(
            a.minX to a.minZ, a.minX to (a.maxZ + 1),
            (a.maxX + 1) to a.minZ, (a.maxX + 1) to (a.maxZ + 1)
        )
        for ((cx, cz) in candidates) {
            val touchesB = (cx == b.minX || cx == b.maxX + 1) && (cz == b.minZ || cz == b.maxZ + 1)
            if (touchesB && !cornerIsEdgeShared(a, b, cx, cz)) return cx to cz
        }
        return null
    }

    private fun cornerIsEdgeShared(a: WalkPolygon, b: WalkPolygon, cx: Int, cz: Int): Boolean {
        val xRangesTouch = a.maxX + 1 == b.minX || b.maxX + 1 == a.minX
        val zOverlap = max(a.minZ, b.minZ) <= min(a.maxZ, b.maxZ)
        if (xRangesTouch && zOverlap) return true
        val zRangesTouch = a.maxZ + 1 == b.minZ || b.maxZ + 1 == a.minZ
        val xOverlap = max(a.minX, b.minX) <= min(a.maxX, b.maxX)
        return zRangesTouch && xOverlap
    }

    private fun diagonalCornerClear(cx: Int, cz: Int, low: WalkPolygon, high: WalkPolygon): Boolean {
        val cells = listOf(cx - 1 to cz - 1, cx to cz - 1, cx - 1 to cz, cx to cz)
        val low4 = cells.firstOrNull { (x, z) -> low.contains(x, z) } ?: return false
        val high4 = cells.firstOrNull { (x, z) -> high.contains(x, z) } ?: return false
        if (low4 == high4) return false
        val sharesX = low4.first == high4.first
        val sharesZ = low4.second == high4.second
        if (sharesX || sharesZ) return false
        val adj1x = low4.first; val adj1z = high4.second
        val adj2x = high4.first; val adj2z = low4.second
        val feetY = high.surfaceY
        return BlockCache.isBodyClearAt(adj1x + 0.5, feetY, adj1z + 0.5) ||
            BlockCache.isBodyClearAt(adj2x + 0.5, feetY, adj2z + 0.5)
    }

    private fun linkCliffFallPortals(polys: List<WalkPolygon>) {
        val columnIndex = buildColumnIndex(polys)
        val alreadyLinked = HashSet<Long>()
        for (high in polys) {
            for (poly in high.portals.map { it.opposite(high) }) {
                alreadyLinked += pairKey(high.id, poly.id)
            }
        }
        val cursor = BlockPos.MutableBlockPos()
        for (high in polys) {
            for ((bx, bz) in cliffEntryCells(high)) {
                val key = columnKey(bx, bz)
                val column = columnIndex[key] ?: continue
                val landing = column.firstOrNull { it.surfaceY < high.surfaceY - FALL_TOLERANCE } ?: continue
                val pair = pairKey(high.id, landing.id)
                if (pair in alreadyLinked) continue
                val drop = high.surfaceY - landing.surfaceY
                if (drop > LONG_FALL_LIMIT) continue
                if (!openVerticalColumn(bx, bz, landing.surfaceY, high.surfaceY, cursor)) continue
                val (l, r) = cliffEntryEdge(high, bx, bz, landing.surfaceY)
                val portal = WalkPortal(high, landing, l, r, isHeightStep = true)
                high.portals += portal
                landing.portals += portal
                alreadyLinked += pair
            }
        }
    }

    private fun buildColumnIndex(polys: List<WalkPolygon>): Map<Long, List<WalkPolygon>> {
        val out = HashMap<Long, MutableList<WalkPolygon>>()
        for (poly in polys) {
            for (x in poly.minX..poly.maxX) for (z in poly.minZ..poly.maxZ) {
                out.getOrPut(columnKey(x, z)) { mutableListOf() } += poly
            }
        }
        out.values.forEach { it.sortByDescending { p -> p.surfaceY } }
        return out
    }

    private fun cliffEntryCells(high: WalkPolygon): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (z in high.minZ..high.maxZ) {
            out += (high.minX - 1) to z
            out += (high.maxX + 1) to z
        }
        for (x in high.minX..high.maxX) {
            out += x to (high.minZ - 1)
            out += x to (high.maxZ + 1)
        }
        return out
    }

    private fun openVerticalColumn(x: Int, z: Int, lowY: Double, highY: Double, cursor: BlockPos.MutableBlockPos): Boolean {
        val scanFloor = floor(lowY).toInt() + 1
        val scanCeiling = floor(highY).toInt()
        for (y in scanFloor..scanCeiling) {
            cursor.set(x, y, z)
            val shape = BlockCache.getCollisionShape(cursor)
            if (shape.isEmpty) continue
            val top = y + shape.max(Direction.Axis.Y)
            if (top <= lowY + GROUND_EPSILON) continue
            return false
        }
        return true
    }

    private fun cliffEntryEdge(high: WalkPolygon, bx: Int, bz: Int, y: Double): Pair<Vec3, Vec3> {
        return when {
            bx < high.minX -> Vec3((bx + 1).toDouble(), y, bz.toDouble()) to Vec3((bx + 1).toDouble(), y, (bz + 1).toDouble())
            bx > high.maxX -> Vec3(bx.toDouble(), y, bz.toDouble()) to Vec3(bx.toDouble(), y, (bz + 1).toDouble())
            bz < high.minZ -> Vec3(bx.toDouble(), y, (bz + 1).toDouble()) to Vec3((bx + 1).toDouble(), y, (bz + 1).toDouble())
            else -> Vec3(bx.toDouble(), y, bz.toDouble()) to Vec3((bx + 1).toDouble(), y, bz.toDouble())
        }
    }

    private fun columnKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    private fun hasJumpClearance(low: WalkPolygon, high: WalkPolygon): Boolean {
        val scanFloor = floor(low.surfaceY).toInt()
        val scanCeiling = floor(high.surfaceY).toInt() + 2
        return boundaryColumnsAnyClear(low, high) { x, z ->
            verticalCorridorClear(x, z, scanFloor, scanCeiling, low.surfaceY, high.surfaceY)
        }
    }

    private fun hasFallClearance(high: WalkPolygon, low: WalkPolygon): Boolean {
        val scanFloor = floor(low.surfaceY).toInt()
        val scanCeiling = floor(high.surfaceY).toInt() + 1
        return boundaryColumnsAnyClear(low, high) { x, z ->
            verticalCorridorClear(x, z, scanFloor, scanCeiling, low.surfaceY, high.surfaceY)
        }
    }

    private inline fun boundaryColumnsAnyClear(a: WalkPolygon, b: WalkPolygon, test: (Int, Int) -> Boolean): Boolean {
        val xAxis = a.maxX + 1 == b.minX || b.maxX + 1 == a.minX
        val zAxis = a.maxZ + 1 == b.minZ || b.maxZ + 1 == a.minZ
        when {
            xAxis -> {
                val zLow = max(a.minZ, b.minZ)
                val zHigh = min(a.maxZ, b.maxZ)
                if (zLow > zHigh) return false
                val aSideX = if (a.maxX + 1 == b.minX) a.maxX else a.minX
                val bSideX = if (a.maxX + 1 == b.minX) b.minX else b.maxX
                for (z in zLow..zHigh) {
                    if (test(aSideX, z)) return true
                    if (test(bSideX, z)) return true
                }
                return false
            }
            zAxis -> {
                val xLow = max(a.minX, b.minX)
                val xHigh = min(a.maxX, b.maxX)
                if (xLow > xHigh) return false
                val aSideZ = if (a.maxZ + 1 == b.minZ) a.maxZ else a.minZ
                val bSideZ = if (a.maxZ + 1 == b.minZ) b.minZ else b.maxZ
                for (x in xLow..xHigh) {
                    if (test(x, aSideZ)) return true
                    if (test(x, bSideZ)) return true
                }
                return false
            }
            else -> return false
        }
    }

    private fun verticalCorridorClear(
        x: Int, z: Int, scanFloor: Int, scanCeiling: Int,
        feetLowY: Double, feetHighY: Double
    ): Boolean {
        val lowSupportY = floor(feetLowY).toInt() - 1
        val highSupportY = floor(feetHighY).toInt() - 1
        val bodyCeiling = feetHighY + BlockCache.PLAYER_HEIGHT
        val cursor = BlockPos.MutableBlockPos()
        for (y in scanFloor..scanCeiling) {
            if (y == lowSupportY || y == highSupportY) continue
            cursor.set(x, y, z)
            val shape = BlockCache.getCollisionShape(cursor)
            if (shape.isEmpty) continue
            val shapeBottom = y + shape.min(Direction.Axis.Y)
            val shapeTop = y + shape.max(Direction.Axis.Y)
            if (shapeTop <= feetLowY + GROUND_EPSILON) continue
            if (shapeBottom >= bodyCeiling) continue
            val sitsOnLow = abs(shapeTop - feetLowY) < GROUND_EPSILON
            val sitsOnHigh = abs(shapeTop - feetHighY) < GROUND_EPSILON
            if (!sitsOnLow && !sitsOnHigh) return false
        }
        return true
    }
}
