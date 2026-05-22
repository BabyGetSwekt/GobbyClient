package gobby.pathfinder.solver

import gobby.pathfinder.PathBlacklist
import gobby.pathfinder.navmesh.WalkMesh
import gobby.pathfinder.navmesh.WalkPolygon
import gobby.pathfinder.navmesh.WalkPortal
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object GroundSolver {

    private const val MAX_NODES = 200_000
    private const val OPEN_CLEARANCE_THRESHOLD = 5.0
    private const val WALL_MULT_PER_LEVEL = 3.0
    private const val WALL_FLAT_PER_LEVEL_SQ = 10.0
    private const val MAX_CLIMB_DELTA = BlockCache.MAX_JUMP_RISE
    private const val FALL_COST_PER_BLOCK = 2.0
    private const val STEP_UP_PENALTY = 20.0
    private const val MULTI_BLOCK_JUMP_FACTOR = 25.0
    private const val FRACTIONAL_GROUND_PENALTY = 1.3
    private const val Y_TRIVIAL = 0.001
    private const val PORTAL_SHRINK_FACTOR = 0.45
    private const val PORTAL_SHRINK_MAX = 1.2
    private const val PORTAL_MIN_HALF_WIDTH = 0.3
    private const val CENTER_PULL_FACTOR = 0.4
    private const val CENTER_PULL_MAX = 0.6
    private const val CORNER_MARGIN = 0.35
    private const val CORNER_PUSH = 0.4
    private const val LOS_STEP = 0.3
    private const val LOS_MIN_DIST = 0.5
    private const val LOS_MAX_DIST = 50.0
    private const val LOS_MAX_AHEAD = 40
    private const val SMOOTH_PASSES = 3
    private const val GROUND_SEARCH_BELOW = -2
    private const val GROUND_SEARCH_ABOVE = 2
    private const val GROUND_BODY_EPSILON = 0.01

    fun solve(mesh: WalkMesh, start: Vec3, goal: Vec3): List<Vec3> {
        if (mesh.isEmpty) return emptyList()
        val startPoly = mesh.polygonContaining(start) ?: mesh.nearestPolygon(start) ?: return emptyList()
        val goalPoly = mesh.polygonContaining(goal) ?: mesh.nearestPolygon(goal) ?: return emptyList()
        if (startPoly === goalPoly) return nudgeOffCorners(listOf(start, goalPoly.nearestPointTo(goal)))

        val polyPath = aStarPolys(startPoly, goalPoly) ?: return emptyList()
        val portalSeam = portalSequence(polyPath).map { shrinkPortal(it.first, it.second) }
        val funnelOut = stringPull(start, goalPoly.nearestPointTo(goal), portalSeam, polyPath)
        val refined = if (segmentsClear(funnelOut)) funnelOut else fallbackThroughPortals(start, goal, portalSeam, polyPath)
        val pruned = multiPassPrune(refined)
        val centered = pullToCorridorCenter(pruned, polyPath)
        return nudgeOffCorners(centered)
    }

    private fun pullToCorridorCenter(path: List<Vec3>, corridor: List<WalkPolygon>): List<Vec3> {
        if (path.size <= 2 || corridor.isEmpty()) return path
        val out = ArrayList<Vec3>(path.size)
        out += path.first()
        for (i in 1 until path.size - 1) {
            val wp = path[i]
            val poly = corridor.firstOrNull { it.contains(floor(wp.x).toInt(), floor(wp.z).toInt()) }
            if (poly == null) { out += wp; continue }
            val pull = min(CENTER_PULL_MAX, poly.wallClearance * CENTER_PULL_FACTOR)
            if (pull <= 0.0) { out += wp; continue }
            val center = poly.centerVec()
            val toCenterX = center.x - wp.x
            val toCenterZ = center.z - wp.z
            val dist = sqrt(toCenterX * toCenterX + toCenterZ * toCenterZ)
            if (dist < 0.01) { out += wp; continue }
            val step = min(pull, dist)
            out += Vec3(wp.x + (toCenterX / dist) * step, wp.y, wp.z + (toCenterZ / dist) * step)
        }
        out += path.last()
        return out
    }

    private data class SearchEntry(
        val poly: WalkPolygon,
        val parent: SearchEntry?,
        val gCost: Double,
        val fCost: Double
    )

    private fun aStarPolys(from: WalkPolygon, to: WalkPolygon): List<WalkPolygon>? {
        val open = PriorityQueue<SearchEntry>(compareBy { it.fCost })
        val best = HashMap<Int, Double>()
        val visited = HashSet<Int>()

        open += SearchEntry(from, null, 0.0, heuristic(from, to))
        best[from.id] = 0.0

        var examined = 0
        while (open.isNotEmpty() && examined < MAX_NODES) {
            val cur = open.poll()
            if (!visited.add(cur.poly.id)) continue
            examined++
            if (cur.poly === to) return reconstruct(cur)

            for (portal in cur.poly.portals) {
                val nxt = portal.opposite(cur.poly)
                if (nxt.id in visited) continue
                if (nxt.surfaceY - cur.poly.surfaceY > MAX_CLIMB_DELTA) continue

                val tentative = cur.gCost + edgeCost(cur.poly, portal, nxt)
                val seenBest = best[nxt.id]
                if (seenBest != null && seenBest <= tentative) continue

                best[nxt.id] = tentative
                open += SearchEntry(nxt, cur, tentative, tentative + heuristic(nxt, to))
            }
        }
        return null
    }

    private fun edgeCost(from: WalkPolygon, portal: WalkPortal, to: WalkPolygon): Double {
        val baseDistance = from.centerVec().distanceTo(to.centerVec())
        val clearance = to.wallClearance.toDouble()
        val wallScore = max(0.0, OPEN_CLEARANCE_THRESHOLD - clearance)
        val clearanceMult = 1.0 + wallScore * WALL_MULT_PER_LEVEL
        val flatWallPenalty = wallScore * wallScore * WALL_FLAT_PER_LEVEL_SQ
        var cost = baseDistance * clearanceMult * PathBlacklist.penaltyFor(to) + flatWallPenalty

        val fracY = ((to.surfaceY % 1.0) + 1.0) % 1.0
        if (fracY > Y_TRIVIAL && fracY < 1.0 - Y_TRIVIAL) cost *= FRACTIONAL_GROUND_PENALTY

        if (portal.isHeightStep) {
            val deltaY = to.surfaceY - from.surfaceY
            cost += when {
                deltaY < 0 -> -deltaY * FALL_COST_PER_BLOCK
                deltaY <= 1.0 -> STEP_UP_PENALTY
                else -> deltaY * MULTI_BLOCK_JUMP_FACTOR
            }
        }
        return cost
    }

    private fun heuristic(p: WalkPolygon, goal: WalkPolygon): Double =
        p.centerVec().distanceTo(goal.centerVec())

    private fun reconstruct(end: SearchEntry): List<WalkPolygon> {
        val out = ArrayList<WalkPolygon>()
        var cur: SearchEntry? = end
        while (cur != null) {
            out += cur.poly
            cur = cur.parent
        }
        return out.asReversed()
    }

    private fun portalSequence(path: List<WalkPolygon>): List<Pair<Vec3, Vec3>> {
        if (path.size < 2) return emptyList()
        val seam = ArrayList<Pair<Vec3, Vec3>>(path.size - 1)
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val portal = a.portals.firstOrNull { it.opposite(a) === b } ?: return emptyList()
            seam += orient(a, portal)
        }
        return seam
    }

    private fun orient(from: WalkPolygon, portal: WalkPortal): Pair<Vec3, Vec3> {
        val center = from.centerVec()
        val cross = (portal.right.x - portal.left.x) * (center.z - portal.left.z) -
                (portal.right.z - portal.left.z) * (center.x - portal.left.x)
        return if (cross < 0) portal.right to portal.left else portal.left to portal.right
    }

    private fun shrinkPortal(left: Vec3, right: Vec3): Pair<Vec3, Vec3> {
        val width = left.distanceTo(right)
        if (width < 0.01) return left to right
        val mid = left.add(right).scale(0.5)
        val shrinkBy = min(PORTAL_SHRINK_MAX, width * PORTAL_SHRINK_FACTOR)
        val newHalf = max(PORTAL_MIN_HALF_WIDTH, width * 0.5 - shrinkBy)
        val dirX = (right.x - left.x) / width
        val dirZ = (right.z - left.z) / width
        val l = Vec3(mid.x - dirX * newHalf, mid.y, mid.z - dirZ * newHalf)
        val r = Vec3(mid.x + dirX * newHalf, mid.y, mid.z + dirZ * newHalf)
        return l to r
    }

    private fun stringPull(start: Vec3, goal: Vec3, portals: List<Pair<Vec3, Vec3>>, corridor: List<WalkPolygon>): List<Vec3> {
        if (portals.isEmpty()) return listOf(start, goal)
        val terminated = portals + listOf(goal to goal)

        val out = ArrayList<Vec3>()
        out += start
        var apex = start
        var leftAnchor = portals.first().first
        var rightAnchor = portals.first().second
        var apexIdx = 0; var leftIdx = 0; var rightIdx = 0

        var i = 1
        while (i < terminated.size) {
            val (curLeft, curRight) = terminated[i]

            if (triangleArea(apex, rightAnchor, curRight) <= 0.0) {
                if (apex == rightAnchor || triangleArea(apex, leftAnchor, curRight) > 0.0) {
                    rightAnchor = curRight; rightIdx = i
                } else {
                    out += withCorridorY(leftAnchor, corridor, leftIdx)
                    apex = leftAnchor; apexIdx = leftIdx
                    leftAnchor = apex; rightAnchor = apex
                    leftIdx = apexIdx; rightIdx = apexIdx
                    i = apexIdx + 1
                    continue
                }
            }
            if (triangleArea(apex, leftAnchor, curLeft) >= 0.0) {
                if (apex == leftAnchor || triangleArea(apex, rightAnchor, curLeft) < 0.0) {
                    leftAnchor = curLeft; leftIdx = i
                } else {
                    out += withCorridorY(rightAnchor, corridor, rightIdx)
                    apex = rightAnchor; apexIdx = rightIdx
                    leftAnchor = apex; rightAnchor = apex
                    leftIdx = apexIdx; rightIdx = apexIdx
                    i = apexIdx + 1
                    continue
                }
            }
            i++
        }
        out += goal
        return dedupConsecutive(out)
    }

    private fun withCorridorY(point: Vec3, corridor: List<WalkPolygon>, portalIdx: Int): Vec3 {
        if (portalIdx + 1 < corridor.size) return Vec3(point.x, corridor[portalIdx + 1].surfaceY, point.z)
        return point
    }

    private fun fallbackThroughPortals(start: Vec3, goal: Vec3, portals: List<Pair<Vec3, Vec3>>, corridor: List<WalkPolygon>): List<Vec3> {
        val out = ArrayList<Vec3>(portals.size + 2)
        out += start
        for ((i, portal) in portals.withIndex()) {
            val mid = portal.first.add(portal.second).scale(0.5)
            val y = if (i + 1 < corridor.size) corridor[i + 1].surfaceY else mid.y
            out += Vec3(mid.x, y, mid.z)
        }
        out += goal
        return dedupConsecutive(out)
    }

    private fun dedupConsecutive(path: List<Vec3>): List<Vec3> {
        val out = ArrayList<Vec3>(path.size)
        for (p in path) if (out.isEmpty() || !nearlyEqual(out.last(), p)) out += p
        return out
    }

    private fun nearlyEqual(a: Vec3, b: Vec3): Boolean =
        abs(a.x - b.x) < 0.001 && abs(a.z - b.z) < 0.001

    private fun multiPassPrune(path: List<Vec3>): List<Vec3> {
        if (path.size <= 2) return path
        var current = path
        repeat(SMOOTH_PASSES) {
            if (current.size <= 2) return current
            val next = singlePassPrune(current)
            if (next.size >= current.size) return current
            current = next
        }
        return current
    }

    private fun singlePassPrune(path: List<Vec3>): List<Vec3> {
        val out = ArrayList<Vec3>()
        out += path.first()
        var anchor = 0
        while (anchor < path.size - 1) {
            var farthest = anchor + 1
            val ceiling = min(path.lastIndex, anchor + LOS_MAX_AHEAD)
            for (probe in ceiling downTo anchor + 2) {
                if (path[anchor].distanceTo(path[probe]) > LOS_MAX_DIST) continue
                if (hasGroundedLineOfSight(path[anchor], path[probe])) { farthest = probe; break }
            }
            out += path[farthest]
            anchor = farthest
        }
        return out
    }

    private fun segmentsClear(path: List<Vec3>): Boolean {
        for (i in 0 until path.size - 1) {
            if (!hasGroundedLineOfSight(path[i], path[i + 1])) return false
        }
        return true
    }

    private fun hasGroundedLineOfSight(from: Vec3, to: Vec3): Boolean {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < LOS_MIN_DIST) return true

        val perpX = -dz / dist
        val perpZ = dx / dist
        val steps = ceil(dist / LOS_STEP).toInt()
        val dy = to.y - from.y
        val lateralOffsets = doubleArrayOf(0.0)

        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val sx = from.x + dx * t
            val sy = from.y + dy * t
            val sz = from.z + dz * t

            val groundY = findGroundY(sx, sy, sz) ?: return false

            for (offset in lateralOffsets) {
                val px = sx + perpX * offset
                val pz = sz + perpZ * offset
                if (!bodyClearAt(px, groundY, pz)) return false
            }
        }
        return true
    }

    private fun findGroundY(x: Double, approxY: Double, z: Double): Double? {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val cursor = BlockPos.MutableBlockPos()
        for (yOff in GROUND_SEARCH_ABOVE downTo GROUND_SEARCH_BELOW) {
            val by = floor(approxY - 0.05 + yOff).toInt()
            cursor.set(bx, by, bz)
            val shape = BlockCache.getCollisionShape(cursor)
            if (shape.isEmpty) continue
            val top = by + shape.max(Direction.Axis.Y)
            if (top >= approxY - 2.0 && top <= approxY + 1.5) return top
        }
        return null
    }

    private fun bodyClearAt(x: Double, feetY: Double, z: Double): Boolean {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val minBlockY = floor(feetY).toInt()
        val maxBlockY = floor(feetY + BlockCache.PLAYER_HEIGHT).toInt()
        val cursor = BlockPos.MutableBlockPos()
        for (by in minBlockY..maxBlockY) {
            cursor.set(bx, by, bz)
            val shape = BlockCache.getCollisionShape(cursor)
            if (shape.isEmpty) continue
            val bottom = by + shape.min(Direction.Axis.Y)
            val top = by + shape.max(Direction.Axis.Y)
            if (top > feetY + GROUND_BODY_EPSILON && bottom < feetY + BlockCache.PLAYER_HEIGHT) return false
        }
        return true
    }

    private fun nudgeOffCorners(path: List<Vec3>): List<Vec3> {
        if (path.size <= 2) return path
        val out = ArrayList<Vec3>(path.size)
        out += path.first()
        for (i in 1 until path.size - 1) {
            val wp = path[i]
            val fx = wp.x - floor(wp.x)
            val fz = wp.z - floor(wp.z)
            val nx = when {
                fx < CORNER_MARGIN -> floor(wp.x) + CORNER_PUSH
                fx > 1.0 - CORNER_MARGIN -> floor(wp.x) + 1.0 - CORNER_PUSH
                else -> wp.x
            }
            val nz = when {
                fz < CORNER_MARGIN -> floor(wp.z) + CORNER_PUSH
                fz > 1.0 - CORNER_MARGIN -> floor(wp.z) + 1.0 - CORNER_PUSH
                else -> wp.z
            }
            out += Vec3(nx, wp.y, nz)
        }
        out += path.last()
        return out
    }

    private fun triangleArea(a: Vec3, b: Vec3, c: Vec3): Double =
        (b.x - a.x) * (c.z - a.z) - (c.x - a.x) * (b.z - a.z)
}
