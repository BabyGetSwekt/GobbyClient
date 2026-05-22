package gobby.pathfinder.solver

import gobby.pathfinder.world.BlockCache
import gobby.utils.timer.Clock
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

object VoxelGroundSolver {

    private const val MAX_NODES = 100_000
    private const val MAX_PLAN_MS = 1500L
    private const val HEURISTIC_WEIGHT = 1.15
    private const val COST_CARDINAL = 1.0
    private const val COST_DIAGONAL = 1.414
    private const val COST_STEP_UP = 1.3
    private const val COST_DESCEND = 0.95
    private const val COST_FALL_PER_BLOCK = 1.2
    private const val FALL_LIMIT = 5
    private const val GOAL_REACH_DIST_SQ = 1.5
    private const val WALL_PENALTY_PER_NEIGHBOR = 0.8
    private const val CLIFF_PENALTY_PER_NEIGHBOR = 1.5

    private data class Node(val x: Int, val y: Int, val z: Int, val feetY: Double) {
        fun packKey(): Long {
            val xx = (x.toLong() and 0x3FFFFFF)
            val zz = (z.toLong() and 0x3FFFFFF)
            val yy = (y.toLong() and 0xFFF)
            return (xx shl 38) or (zz shl 12) or yy
        }
    }

    private class Entry(val node: Node, val g: Double, val f: Double, val parent: Entry?)

    fun solve(start: Vec3, goal: Vec3): List<Vec3> {
        val startStand = snapToGround(start) ?: return emptyList()
        val goalStand = snapToGround(goal) ?: return emptyList()
        return aStar(startStand, goalStand)
    }

    private fun snapToGround(pos: Vec3): Node? {
        val bx = floor(pos.x).toInt()
        val bz = floor(pos.z).toInt()
        val surfaces = BlockCache.getStandableSurfaces(bx, bz, pos.y - 2.5, pos.y + 1.5)
        val best = surfaces.minByOrNull { abs(it.feetY - pos.y) } ?: return null
        return Node(bx, best.pos.y, bz, best.feetY)
    }

    private fun aStar(start: Node, goal: Node): List<Vec3> {
        val open = PriorityQueue<Entry>(compareBy { it.f })
        val closed = HashMap<Long, Double>()
        val startEntry = Entry(start, 0.0, heuristic(start, goal), null)
        open += startEntry
        closed[start.packKey()] = 0.0
        var expanded = 0
        var bestNear: Entry = startEntry
        var bestNearH = heuristic(start, goal)
        val deadlineClock = Clock(MAX_PLAN_MS)
        while (open.isNotEmpty() && expanded < MAX_NODES) {
            if ((expanded and 63) == 0 && deadlineClock.hasTimePassed()) break
            val cur = open.poll()
            val recorded = closed[cur.node.packKey()] ?: Double.MAX_VALUE
            if (cur.g > recorded + 1e-6) continue
            expanded++
            if (isAtGoal(cur.node, goal)) return reconstruct(cur)
            val h = heuristic(cur.node, goal)
            if (h < bestNearH) { bestNearH = h; bestNear = cur }
            expandNeighbors(cur, goal, open, closed)
        }
        return reconstruct(bestNear)
    }

    private fun isAtGoal(n: Node, goal: Node): Boolean {
        val dx = n.x - goal.x
        val dz = n.z - goal.z
        val dy = abs(n.y - goal.y)
        return dx * dx + dz * dz <= GOAL_REACH_DIST_SQ && dy <= 1
    }

    private fun heuristic(a: Node, b: Node): Double {
        val dx = abs(a.x - b.x).toDouble()
        val dy = abs(a.feetY - b.feetY)
        val dz = abs(a.z - b.z).toDouble()
        val flat = max(dx, dz) * COST_CARDINAL + (max(dx, dz) - kotlin.math.min(dx, dz)) * 0.0 +
            kotlin.math.min(dx, dz) * (COST_DIAGONAL - COST_CARDINAL)
        return flat + dy * 0.5
    }

    private fun expandNeighbors(
        cur: Entry,
        goal: Node,
        open: PriorityQueue<Entry>,
        closed: HashMap<Long, Double>
    ) {
        for (dir in MOVES) {
            tryMove(cur, dir.dx, dir.dz, dir.diag, goal, open, closed)
        }
    }

    private fun tryMove(
        cur: Entry,
        dx: Int,
        dz: Int,
        diag: Boolean,
        goal: Node,
        open: PriorityQueue<Entry>,
        closed: HashMap<Long, Double>
    ) {
        val baseCost = if (diag) COST_DIAGONAL else COST_CARDINAL
        val nx = cur.node.x + dx
        val nz = cur.node.z + dz

        if (diag && !diagonalAllowed(cur.node, dx, dz)) return

        for (dy in -1..1) {
            val ny = cur.node.y + dy
            val standable = findStandableAtVoxel(nx, ny, nz, cur.node.feetY) ?: continue
            val deltaY = standable.feetY - cur.node.feetY
            if (deltaY > BlockCache.MAX_JUMP_RISE) continue
            if (!headroomClear(cur.node, nx, standable.feetY, nz)) continue
            val cost = baseCost + when {
                dy > 0 -> COST_STEP_UP - COST_CARDINAL
                dy < 0 -> -(COST_CARDINAL - COST_DESCEND)
                else -> 0.0
            }
            val terrainCost = terrainPenalty(nx, nz, standable.feetY)
            pushNeighbor(cur, Node(nx, standable.pos.y, nz, standable.feetY), cur.g + cost + terrainCost, goal, open, closed)
        }

        if (!diag) {
            for (drop in 2..FALL_LIMIT) {
                val ny = cur.node.y - drop
                val standable = findStandableAtVoxel(nx, ny, nz, cur.node.feetY - drop.toDouble()) ?: continue
                if (!fallClear(cur.node, nx, standable.feetY, nz)) continue
                val cost = baseCost + drop * COST_FALL_PER_BLOCK
                val terrainCost = terrainPenalty(nx, nz, standable.feetY)
                pushNeighbor(cur, Node(nx, standable.pos.y, nz, standable.feetY), cur.g + cost + terrainCost, goal, open, closed)
                break
            }
        }
    }

    private fun terrainPenalty(x: Int, z: Int, feetY: Double): Double {
        var solidNeighbors = 0
        var cliffNeighbors = 0
        val midY = feetY + BlockCache.PLAYER_HEIGHT * 0.5
        val belowFeet = floor(feetY - 0.05).toInt()
        for (i in CARDINAL_DX.indices) {
            val ax = x + CARDINAL_DX[i]
            val az = z + CARDINAL_DZ[i]
            if (!BlockCache.isBodyClearAt(ax + 0.5, midY, az + 0.5)) solidNeighbors++
            if (BlockCache.isPassable(BlockPos(ax, belowFeet, az))) cliffNeighbors++
        }
        return solidNeighbors * WALL_PENALTY_PER_NEIGHBOR + cliffNeighbors * CLIFF_PENALTY_PER_NEIGHBOR
    }

    private val CARDINAL_DX = intArrayOf(1, -1, 0, 0)
    private val CARDINAL_DZ = intArrayOf(0, 0, 1, -1)

    private fun diagonalAllowed(cur: Node, dx: Int, dz: Int): Boolean {
        val midY = cur.feetY + BlockCache.PLAYER_HEIGHT * 0.5
        val belowFeet = floor(cur.feetY - 0.05).toInt()
        val c1Clear = BlockCache.isBodyClearAt((cur.x + dx) + 0.5, cur.feetY, cur.z + 0.5) &&
            BlockCache.isBodyClearAt((cur.x + dx) + 0.5, midY, cur.z + 0.5)
        val c2Clear = BlockCache.isBodyClearAt(cur.x + 0.5, cur.feetY, (cur.z + dz) + 0.5) &&
            BlockCache.isBodyClearAt(cur.x + 0.5, midY, (cur.z + dz) + 0.5)
        if (!c1Clear && !c2Clear) return false
        val c1Solid = BlockCache.isSolid(BlockPos(cur.x + dx, belowFeet, cur.z))
        val c2Solid = BlockCache.isSolid(BlockPos(cur.x, belowFeet, cur.z + dz))
        return (c1Clear && c1Solid) || (c2Clear && c2Solid)
    }

    private fun findStandableAtVoxel(x: Int, y: Int, z: Int, anchorFeetY: Double): BlockCache.StandSurface? {
        val minFeet = y.toDouble() - 0.1
        val maxFeet = y.toDouble() + 1.1
        val surfaces = BlockCache.getStandableSurfaces(x, z, minFeet, maxFeet)
        return surfaces.minByOrNull { abs(it.feetY - anchorFeetY) }
    }

    private fun headroomClear(from: Node, tx: Int, tFeetY: Double, tz: Int): Boolean {
        val standY = max(from.feetY, tFeetY)
        return BlockCache.isBodyClearAt(tx + 0.5, standY, tz + 0.5)
    }

    private fun fallClear(from: Node, tx: Int, tFeetY: Double, tz: Int): Boolean {
        val cx = (from.x + tx) / 2.0 + 0.5
        val cz = (from.z + tz) / 2.0 + 0.5
        var y = from.feetY
        while (y > tFeetY) {
            if (!BlockCache.isBodyClearAt(cx, y, cz)) return false
            y -= 0.5
        }
        return true
    }

    private fun pushNeighbor(
        cur: Entry,
        nb: Node,
        g: Double,
        goal: Node,
        open: PriorityQueue<Entry>,
        closed: HashMap<Long, Double>
    ) {
        val key = nb.packKey()
        val prev = closed[key]
        if (prev != null && prev <= g) return
        closed[key] = g
        val f = g + heuristic(nb, goal) * HEURISTIC_WEIGHT
        open += Entry(nb, g, f, cur)
    }

    private fun reconstruct(end: Entry): List<Vec3> {
        val out = ArrayList<Vec3>()
        var cur: Entry? = end
        while (cur != null) {
            val n = cur.node
            out += Vec3(n.x + 0.5, n.feetY, n.z + 0.5)
            cur = cur.parent
        }
        return out.asReversed()
    }

    private data class Move(val dx: Int, val dz: Int, val diag: Boolean)

    private val MOVES = arrayOf(
        Move(1, 0, false), Move(-1, 0, false), Move(0, 1, false), Move(0, -1, false),
        Move(1, 1, true), Move(1, -1, true), Move(-1, 1, true), Move(-1, -1, true)
    )
}
