package gobby.pathfinder.solver

import gobby.pathfinder.JumpProfile
import gobby.pathfinder.PathBlacklist
import gobby.pathfinder.STEP_JUMP_MARGIN
import gobby.pathfinder.world.BlockCache
import gobby.utils.timer.Clock
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
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
    private const val SNAP_SPIRAL_RADIUS = 2

    private data class Node(val x: Int, val y: Int, val z: Int, val feetY: Double) {
        fun packKey(): Long {
            val xx = (x.toLong() and 0x3FFFFFF)
            val zz = (z.toLong() and 0x3FFFFFF)
            val yy = (y.toLong() and 0xFFF)
            return (xx shl 38) or (zz shl 12) or yy
        }
    }

    private class Entry(val node: Node, val g: Double, val f: Double, val parent: Entry?)

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
        val startStand = snapToGround(start) ?: return PathResult.EMPTY
        val goalStand = snapToGround(goal) ?: return PathResult.EMPTY
        return aStar(startStand, goalStand)
    }

    private fun snapToGround(pos: Vec3): Node? {
        val centerX = floor(pos.x).toInt()
        val centerZ = floor(pos.z).toInt()
        var fallback: BlockCache.StandSurface? = null
        for (radius in 0..SNAP_SPIRAL_RADIUS) {
            var best: BlockCache.StandSurface? = null
            for (dx in -radius..radius) for (dz in -radius..radius) {
                if (max(abs(dx), abs(dz)) != radius) continue
                val surfaces = BlockCache.getStandableSurfaces(centerX + dx, centerZ + dz, pos.y - 2.5, pos.y + 1.5)
                val candidate = surfaces.minByOrNull { abs(it.feetY - pos.y) } ?: continue
                if (fallback == null) fallback = candidate
                if (radius > 0 && !snapReachable(pos, candidate)) continue
                if (best == null || abs(candidate.feetY - pos.y) < abs(best.feetY - pos.y)) best = candidate
            }
            best?.let { return Node(it.pos.x, it.pos.y, it.pos.z, it.feetY) }
        }
        return fallback?.let { Node(it.pos.x, it.pos.y, it.pos.z, it.feetY) }
    }

    private fun snapReachable(from: Vec3, surface: BlockCache.StandSurface): Boolean {
        val passY = max(from.y, surface.feetY)
        val steps = max(2, (max(abs(surface.pos.x + 0.5 - from.x), abs(surface.pos.z + 0.5 - from.z)) * 2).toInt())
        return BlockCache.isSweepClear(from.x, passY, from.z, surface.pos.x + 0.5, passY, surface.pos.z + 0.5, steps) { passY }
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
                return PathResult(reconstruct(cur), complete = true, remainingDistance = 0.0, exhausted = false, frontierUnknown = frontierTouchedUnknown)
            }
            val h = heuristic(cur.node, goal)
            if (h < bestNearH) { bestNearH = h; bestNear = cur }
            expandNeighbors(cur, goal, jumpProfile, open, closed)
        }
        return PathResult(
            reconstruct(bestNear),
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

        val maxDy = ceil(jumpProfile.maxClimb).toInt().coerceAtLeast(1)
        for (dy in -1..maxDy) {
            val ny = cur.node.y + dy
            val standable = findStandableAtVoxel(nx, ny, nz, cur.node.feetY) ?: continue
            val deltaY = standable.feetY - cur.node.feetY
            if (deltaY > jumpProfile.maxClimb) continue
            if (!headroomClear(cur.node, nx, standable.feetY, nz)) continue
            if (diag && !diagonalAllowed(cur.node, dx, dz, standable.feetY)) continue
            val cost = baseCost + turnCost + when {
                deltaY > jumpProfile.stepHeight + STEP_JUMP_MARGIN -> COST_JUMP_RISE - COST_CARDINAL
                dy > 0 -> COST_STEP_UP - COST_CARDINAL
                dy < 0 -> -(COST_CARDINAL - COST_DESCEND)
                else -> 0.0
            }
            val node = Node(nx, standable.pos.y, nz, standable.feetY)
            if (dominated(node, cur.g + cost, closed)) continue
            pushNeighbor(cur, node, cur.g + cost + terrainPenalty(nx, nz, standable.feetY), goal, open, closed)
        }

        if (!diag) {
            val landing = findFallLandingAtVoxel(nx, nz, cur.node.feetY)
            if (landing != null && fallClear(cur.node, nx, landing.feetY, nz)) {
                val drop = cur.node.feetY - landing.feetY
                val cost = baseCost + turnCost + drop * COST_FALL_PER_BLOCK
                val node = Node(nx, landing.pos.y, nz, landing.feetY)
                if (dominated(node, cur.g + cost, closed)) return
                pushNeighbor(cur, node, cur.g + cost + terrainPenalty(nx, nz, landing.feetY), goal, open, closed)
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
        for (dx in -maxSkip..maxSkip) for (dz in -maxSkip..maxSkip) {
            if (dx == 0 && dz == 0) continue
            val cheb = max(abs(dx), abs(dz))
            if (cheb <= 1) continue
            val horizontal = sqrt((dx * dx + dz * dz).toDouble())
            if (horizontal > jumpProfile.maxHorizontalBlocks) continue

            val nx = cur.node.x + dx
            val nz = cur.node.z + dz
            if (!BlockCache.isChunkAvailable(nx, nz)) {
                frontierTouchedUnknown = true
                continue
            }
            val landing = findJumpLandingAtVoxel(nx, nz, cur.node.feetY, jumpProfile) ?: continue
            val deltaY = landing.feetY - cur.node.feetY
            if (deltaY <= jumpProfile.stepHeight + STEP_JUMP_MARGIN) continue

            val cost = COST_JUMP_ACTION + horizontal * COST_JUMP_SKIP_PER_BLOCK + deltaY * COST_STEP_UP * 2.0
            val node = Node(nx, landing.pos.y, nz, landing.feetY)
            if (dominated(node, cur.g + cost, closed)) continue
            if (!jumpArcClear(cur.node, nx, landing.feetY, nz, jumpProfile)) continue
            pushNeighbor(cur, node, cur.g + cost + terrainPenalty(nx, nz, landing.feetY), goal, open, closed)
        }
    }

    private fun terrainPenalty(x: Int, z: Int, feetY: Double): Double {
        var wallNeighbors = 0
        var cliffNeighbors = 0
        val midY = feetY + BlockCache.PLAYER_HEIGHT * 0.5
        for (i in CARDINAL_DX.indices) {
            val ax = x + CARDINAL_DX[i]
            val az = z + CARDINAL_DZ[i]
            if (!BlockCache.isChunkAvailable(ax, az)) continue
            val reachableSurfaces = BlockCache.getStandableSurfaces(
                ax, az,
                feetY - NEIGHBOR_DROP_LIMIT,
                feetY + BlockCache.MAX_JUMP_RISE
            )
            if (reachableSurfaces.isNotEmpty()) continue
            if (!BlockCache.isBodyClearAt(ax + 0.5, midY, az + 0.5)) wallNeighbors++ else cliffNeighbors++
        }
        val base = wallNeighbors * WALL_PENALTY_PER_NEIGHBOR + cliffNeighbors * CLIFF_PENALTY_PER_NEIGHBOR
        val blacklistMult = PathBlacklist.penaltyAt(x + 0.5, z + 0.5)
        return base * blacklistMult + (blacklistMult - 1.0)
    }

    private fun dominated(node: Node, gWithoutTerrain: Double, closed: HashMap<Long, Double>): Boolean {
        val prev = closed[node.packKey()]
        return prev != null && prev <= gWithoutTerrain
    }

    private val CARDINAL_DX = intArrayOf(1, -1, 0, 0)
    private val CARDINAL_DZ = intArrayOf(0, 0, 1, -1)

    private fun diagonalAllowed(cur: Node, dx: Int, dz: Int, targetFeetY: Double): Boolean {
        val passFeetY = max(cur.feetY, targetFeetY)
        if (!BlockCache.isBodyClearAt((cur.x + dx) + 0.5, passFeetY, cur.z + 0.5)) return false
        if (!BlockCache.isBodyClearAt(cur.x + 0.5, passFeetY, (cur.z + dz) + 0.5)) return false
        val belowFeet = floor(passFeetY - 0.05).toInt()
        val c1Supported = cornerSupported(cur.x + dx, belowFeet, cur.z)
        val c2Supported = cornerSupported(cur.x, belowFeet, cur.z + dz)
        return c1Supported || c2Supported
    }

    private fun cornerSupported(x: Int, belowFeet: Int, z: Int): Boolean =
        BlockCache.isSolid(BlockPos(x, belowFeet, z)) || BlockCache.isSolid(BlockPos(x, belowFeet - 1, z))

    private fun findStandableAtVoxel(x: Int, y: Int, z: Int, anchorFeetY: Double): BlockCache.StandSurface? {
        val minFeet = y.toDouble() - 0.1
        val maxFeet = y.toDouble() + 1.1
        val surfaces = BlockCache.getStandableSurfaces(x, z, minFeet, maxFeet)
        return surfaces.minByOrNull { abs(it.feetY - anchorFeetY) }
    }

    private fun findFallLandingAtVoxel(x: Int, z: Int, fromFeetY: Double): BlockCache.StandSurface? {
        val minFeet = fromFeetY - FALL_LIMIT
        val maxFeet = fromFeetY - 1.1
        return BlockCache.getStandableSurfaces(x, z, minFeet, maxFeet).firstOrNull()
    }

    private fun findJumpLandingAtVoxel(
        x: Int,
        z: Int,
        fromFeetY: Double,
        jumpProfile: JumpProfile
    ): BlockCache.StandSurface? {
        val minFeet = fromFeetY + jumpProfile.stepHeight + STEP_JUMP_MARGIN
        val maxFeet = fromFeetY + jumpProfile.maxClimb
        return BlockCache.getStandableSurfaces(x, z, minFeet, maxFeet)
            .minByOrNull { abs(it.feetY - maxFeet) }
    }

    private fun headroomClear(from: Node, tx: Int, tFeetY: Double, tz: Int): Boolean {
        val standY = max(from.feetY, tFeetY)
        return BlockCache.isBodyClearAt(tx + 0.5, standY, tz + 0.5)
    }

    private fun fallClear(from: Node, tx: Int, tFeetY: Double, tz: Int): Boolean {
        if (!BlockCache.isSweepClear(
                from.x + 0.5,
                from.feetY,
                from.z + 0.5,
                tx + 0.5,
                from.feetY,
                tz + 0.5,
                2
            ) { from.feetY }
        ) return false

        val cx = tx + 0.5
        val cz = tz + 0.5
        var y = from.feetY
        while (y > tFeetY) {
            if (!BlockCache.isBodyClearAt(cx, y, cz)) return false
            y -= 0.5
        }
        return true
    }

    private fun jumpArcClear(from: Node, tx: Int, tFeetY: Double, tz: Int, jumpProfile: JumpProfile): Boolean {
        val dx = tx + 0.5 - (from.x + 0.5)
        val dz = tz + 0.5 - (from.z + 0.5)
        val dist = sqrt(dx * dx + dz * dz)
        val steps = max(2, ceil(dist / 0.35).toInt())
        val baseDelta = tFeetY - from.feetY
        val arcLift = max(0.6, minOf(jumpProfile.jumpHeight, baseDelta + 0.8))
        return BlockCache.isSweepClear(
            from.x + 0.5,
            from.feetY,
            from.z + 0.5,
            tx + 0.5,
            tFeetY,
            tz + 0.5,
            steps
        ) { t ->
            from.feetY + baseDelta * t + 4.0 * arcLift * t * (1.0 - t)
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
