package gobby.pathfinder.solver

import gobby.pathfinder.JumpProfile
import gobby.pathfinder.STEP_JUMP_MARGIN
import gobby.pathfinder.world.BlockCache
import gobby.utils.timer.Clock
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object VoxelGroundSolver {
    private const val MAX_NODES = 100_000
    private const val MAX_PLAN_MS = 1500L
    private const val HEURISTIC_WEIGHT = 1.15
    private const val HEURISTIC_Y_WEIGHT = 0.5
    private const val COST_CARDINAL = 1.0
    private const val COST_DIAGONAL = 1.414
    private const val COST_STEP_UP = 1.3
    private const val COST_JUMP_RISE = 2.8
    private const val COST_DESCEND = 0.95
    private const val COST_FALL_PER_BLOCK = 0.35
    private const val COST_JUMP_ACTION = 6.0
    private const val COST_JUMP_SKIP_PER_BLOCK = 1.8
    private const val TURN_PENALTY = 0.05
    private const val FALL_LIMIT = 64
    private const val GOAL_REACH_DIST_SQ = 1.5
    private const val WALL_PENALTY_PER_NEIGHBOR = 0.8
    private const val CLIFF_PENALTY_PER_NEIGHBOR = 1.5
    private const val NEIGHBOR_DROP_LIMIT = 2.0
    internal data class Node(val x: Int, val y: Int, val z: Int, val feetY: Double) {
        fun packKey(): Long {
            val xx = (x.toLong() and 0x3FFFFFF)
            val zz = (z.toLong() and 0x3FFFFFF)
            val yy = (y.toLong() and 0xFFF)
            return (xx shl 38) or (zz shl 12) or yy
        }
    }

    internal class Entry(val node: Node, val g: Double, val f: Double, val parent: Entry?)
    data class PathResult(
        val waypoints: List<Vec3>,
        val complete: Boolean,
        val remainingDistance: Double,
        val exhausted: Boolean,
        val frontierUnknown: Boolean
    ) {
        companion object {
            val EMPTY = PathResult(emptyList(), false, Double.MAX_VALUE, exhausted = false, frontierUnknown = false)
        }
    }
    private var frontierTouchedUnknown = false

    fun solve(start: Vec3, goal: Vec3): PathResult {
        frontierTouchedUnknown = false
        val startStand = VoxelGroundSnapper.snap(start) ?: return PathResult.EMPTY
        val goalStand = VoxelGroundSnapper.snap(goal) ?: return PathResult.EMPTY
        return aStar(startStand, goalStand)
    }

    private fun aStar(start: Node, goal: Node): PathResult {
        val jumpProfile = JumpProfile.current()
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
            if (isAtGoal(cur.node, goal)) {
                return PathResult(VoxelPathReconstruction.build(cur), complete = true, remainingDistance = 0.0, exhausted = false, frontierUnknown = frontierTouchedUnknown)
            }
            val h = heuristic(cur.node, goal)
            if (h < bestNearH) {
                bestNearH = h
                bestNear = cur
            }
            expandNeighbors(cur, goal, jumpProfile, open, closed)
        }
        return PathResult(
            VoxelPathReconstruction.build(bestNear),
            complete = false,
            remainingDistance = euclidean(bestNear.node, goal),
            exhausted = open.isEmpty(),
            frontierUnknown = frontierTouchedUnknown
        )
    }

    private fun euclidean(a: Node, b: Node): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = a.feetY - b.feetY
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
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
        val flat = max(dx, dz) * COST_CARDINAL + min(dx, dz) * (COST_DIAGONAL - COST_CARDINAL)
        return flat + dy * HEURISTIC_Y_WEIGHT
    }

    private fun expandNeighbors(
        cur: Entry,
        goal: Node,
        jumpProfile: JumpProfile,
        open: PriorityQueue<Entry>,
        closed: HashMap<Long, Double>
    ) {
        for (dir in MOVES) {
            tryMove(cur, dir.dx, dir.dz, dir.diag, jumpProfile, goal, open, closed)
        }
        if (jumpProfile.maxSkipCells >= 2) {
            expandJumpSkips(cur, goal, jumpProfile, open, closed)
        }
    }

    private fun tryMove(
        cur: Entry,
        dx: Int,
        dz: Int,
        diag: Boolean,
        jumpProfile: JumpProfile,
        goal: Node,
        open: PriorityQueue<Entry>,
        closed: HashMap<Long, Double>
    ) {
        val baseCost = if (diag) COST_DIAGONAL else COST_CARDINAL
        val nx = cur.node.x + dx
        val nz = cur.node.z + dz
        if (!BlockCache.isChunkAvailable(nx, nz)) {
            frontierTouchedUnknown = true
            return
        }
        val turnCost = turnPenalty(cur, dx, dz)
        tryStandableMoves(cur, nx, nz, dx, dz, diag, jumpProfile, goal, open, closed, baseCost, turnCost)

        if (!diag) {
            val landing = VoxelGroundGeometry.fallLandingAt(nx, nz, cur.node.feetY)
            if (landing != null && VoxelGroundGeometry.fallClear(cur.node, nx, landing.feetY, nz)) {
                val drop = cur.node.feetY - landing.feetY
                val cost = baseCost + turnCost + drop * COST_FALL_PER_BLOCK
                val node = Node(nx, landing.pos.y, nz, landing.feetY)
                if (dominated(node, cur.g + cost, closed)) return
                pushNeighbor(cur, node, cur.g + cost + VoxelGroundGeometry.terrainPenalty(nx, nz, landing.feetY), goal, open, closed)
            }
        }
    }

    private fun turnPenalty(cur: Entry, dx: Int, dz: Int): Double {
        val parent = cur.parent ?: return 0.0
        val prevDx = Integer.signum(cur.node.x - parent.node.x)
        val prevDz = Integer.signum(cur.node.z - parent.node.z)
        if (prevDx == 0 && prevDz == 0) return 0.0
        return if (prevDx != dx || prevDz != dz) TURN_PENALTY else 0.0
    }

    private fun expandJumpSkips(
        cur: Entry,
        goal: Node,
        jumpProfile: JumpProfile,
        open: PriorityQueue<Entry>,
        closed: HashMap<Long, Double>
    ) {
        val maxSkip = jumpProfile.maxSkipCells
        val side = maxSkip * 2 + 1
        for (index in 0 until side * side) {
            val dx = index / side - maxSkip
            val dz = index % side - maxSkip
            tryJumpSkip(cur, goal, jumpProfile, open, closed, dx, dz)
        }
    }

    private fun tryJumpSkip(cur: Entry, goal: Node, jumpProfile: JumpProfile, open: PriorityQueue<Entry>, closed: HashMap<Long, Double>, dx: Int, dz: Int) {
        if (dx == 0 && dz == 0 || max(abs(dx), abs(dz)) <= 1) return
        val horizontal = sqrt((dx * dx + dz * dz).toDouble())
        if (horizontal > jumpProfile.maxHorizontalBlocks) return
        val nx = cur.node.x + dx
        val nz = cur.node.z + dz
        if (!BlockCache.isChunkAvailable(nx, nz)) {
            frontierTouchedUnknown = true
            return
        }
        val landing = VoxelGroundGeometry.jumpLandingAt(nx, nz, cur.node.feetY, jumpProfile) ?: return
        val deltaY = landing.feetY - cur.node.feetY
        if (deltaY <= jumpProfile.stepHeight + STEP_JUMP_MARGIN) return
        val cost = COST_JUMP_ACTION + horizontal * COST_JUMP_SKIP_PER_BLOCK + deltaY * COST_STEP_UP * 2.0
        val node = Node(nx, landing.pos.y, nz, landing.feetY)
        if (dominated(node, cur.g + cost, closed) || !VoxelGroundGeometry.jumpArcClear(cur.node, nx, landing.feetY, nz, jumpProfile)) return
        pushNeighbor(cur, node, cur.g + cost + VoxelGroundGeometry.terrainPenalty(nx, nz, landing.feetY), goal, open, closed)
    }

    private fun dominated(node: Node, gWithoutTerrain: Double, closed: HashMap<Long, Double>): Boolean {
        val prev = closed[node.packKey()]
        return prev != null && prev <= gWithoutTerrain
    }

    private fun tryStandableMoves(cur: Entry, nx: Int, nz: Int, dx: Int, dz: Int, diag: Boolean, jumpProfile: JumpProfile, goal: Node, open: PriorityQueue<Entry>, closed: HashMap<Long, Double>, baseCost: Double, turnCost: Double) {
        val maxDy = ceil(jumpProfile.maxClimb).toInt().coerceAtLeast(1)
        for (dy in -1..maxDy) {
            val standable = VoxelGroundGeometry.standableAt(nx, cur.node.y + dy, nz, cur.node.feetY) ?: continue
            val deltaY = standable.feetY - cur.node.feetY
            if (deltaY > jumpProfile.maxClimb || !VoxelGroundGeometry.headroomClear(cur.node, nx, standable.feetY, nz)) continue
            if (diag && !VoxelGroundGeometry.diagonalAllowed(cur.node, dx, dz, standable.feetY)) continue
            val cost = baseCost + turnCost + when {
                deltaY > jumpProfile.stepHeight + STEP_JUMP_MARGIN -> COST_JUMP_RISE - COST_CARDINAL
                dy > 0 -> COST_STEP_UP - COST_CARDINAL
                dy < 0 -> -(COST_CARDINAL - COST_DESCEND)
                else -> 0.0
            }
            val node = Node(nx, standable.pos.y, nz, standable.feetY)
            if (!dominated(node, cur.g + cost, closed)) pushNeighbor(cur, node, cur.g + cost + VoxelGroundGeometry.terrainPenalty(nx, nz, standable.feetY), goal, open, closed)
        }
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
    private data class Move(val dx: Int, val dz: Int, val diag: Boolean)
    private val MOVES = arrayOf(
        Move(1, 0, false), Move(-1, 0, false), Move(0, 1, false), Move(0, -1, false),
        Move(1, 1, true), Move(1, -1, true), Move(-1, 1, true), Move(-1, -1, true)
    )
}
