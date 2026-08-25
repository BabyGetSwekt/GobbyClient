package gobby.pathfinder.solver

import gobby.pathfinder.Cells
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

object SkySolver {

    private const val MAX_NODES = 200_000
    private val DIAG_2D = sqrt(2.0)
    private val DIAG_3D = sqrt(3.0)
    private const val LOS_STEP = 0.4
    private const val MIN_LOS_DIST = 0.5

    fun solve(start: Vec3, goal: Vec3, scanRange: Int): List<Vec3> {
        val from = BlockPos.containing(start)
        val to = BlockPos.containing(goal)
        if (!flightPassable(from) || !flightPassable(to)) return emptyList()

        val expanded = aStar3D(from, to, scanRange) ?: return emptyList()
        val raw = expanded.map { BlockPos(it.x, it.y, it.z).let { p -> Vec3(p.x + 0.5, p.y.toDouble(), p.z + 0.5) } }
        return prune(raw)
    }

    private data class Node(
        val key: Long,
        val pos: BlockPos,
        val parent: Node?,
        val gCost: Double,
        val fCost: Double
    )

    private fun aStar3D(from: BlockPos, to: BlockPos, range: Int): List<BlockPos>? {
        val open = PriorityQueue<Node>(compareBy { it.fCost })
        val closed = HashSet<Long>()
        val seen = HashMap<Long, Node>()

        val startKey = Cells.pack(from)
        val goalKey = Cells.pack(to)
        val startNode = Node(startKey, from, null, 0.0, octile(from, to))
        open += startNode
        seen[startKey] = startNode

        var iterations = 0
        val neighbors = Cells.ALL_26_NEIGHBORS

        while (open.isNotEmpty() && iterations < MAX_NODES) {
            val current = open.poll()
            if (closed.contains(current.key)) continue
            closed += current.key
            iterations++

            if (current.key == goalKey) return rebuild(current)
            if (manhattan(current.pos, from) > range) continue

            expandNeighbors(current, to, neighbors, closed, seen, open)
        }
        return null
    }

    private fun expandNeighbors(current: Node, goal: BlockPos, neighbors: IntArray, closed: Set<Long>, seen: MutableMap<Long, Node>, open: PriorityQueue<Node>) {
        var index = 0
        while (index < neighbors.size) {
            val dx = neighbors[index]
            val dy = neighbors[index + 1]
            val dz = neighbors[index + 2]
            index += 3
            val nextPos = current.pos.offset(dx, dy, dz)
            val nextKey = Cells.pack(nextPos)
            if (nextKey in closed || !flightPassable(nextPos)) continue
            val moveCost = moveCost(dx, dy, dz)
            val tentativeG = current.gCost + moveCost
            val prior = seen[nextKey]
            if (prior == null || tentativeG < prior.gCost) {
                val node = Node(nextKey, nextPos, current, tentativeG, tentativeG + octile(nextPos, goal))
                seen[nextKey] = node
                open += node
            }
        }
    }

    private fun moveCost(dx: Int, dy: Int, dz: Int): Double {
        val axes = (if (dx != 0) 1 else 0) + (if (dy != 0) 1 else 0) + (if (dz != 0) 1 else 0)
        return when (axes) {
            1 -> 1.0
            2 -> DIAG_2D
            else -> DIAG_3D
        }
    }

    private fun rebuild(end: Node): List<BlockPos> {
        val chain = ArrayList<BlockPos>()
        var cur: Node? = end
        while (cur != null) {
            chain += cur.pos
            cur = cur.parent
        }
        return chain.asReversed()
    }

    private fun flightPassable(pos: BlockPos): Boolean =
        BlockCache.isPassable(pos) && BlockCache.isPassable(pos.above())

    private fun manhattan(a: BlockPos, b: BlockPos): Int =
        abs(a.x - b.x) + abs(a.y - b.y) + abs(a.z - b.z)

    private fun octile(a: BlockPos, b: BlockPos): Double =
        Cells.octileHeuristic(a.x, a.y, a.z, b.x, b.y, b.z)

    private fun prune(raw: List<Vec3>): List<Vec3> {
        if (raw.size <= 2) return raw
        val out = ArrayList<Vec3>()
        out += raw.first()
        var anchor = 0
        while (anchor < raw.size - 1) {
            val farthest = farthestReachableIndex(raw, anchor)
            out += raw[farthest]
            anchor = farthest
        }
        return out
    }

    private fun farthestReachableIndex(raw: List<Vec3>, anchor: Int): Int {
        for (probe in raw.lastIndex downTo anchor + 2) {
            if (hasLineOfSight(raw[anchor], raw[probe])) return probe
        }
        return anchor + 1
    }

    private fun hasLineOfSight(from: Vec3, to: Vec3): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        if (distance < MIN_LOS_DIST) return true
        val steps = ceil(distance / LOS_STEP).toInt()
        for (i in 1 until steps) {
            val t = i / steps.toDouble()
            val sampleX = from.x + dx * t
            val sampleY = from.y + dy * t
            val sampleZ = from.z + dz * t
            val feet = BlockPos.containing(sampleX, sampleY, sampleZ)
            if (!flightPassable(feet)) return false
        }
        return true
    }
}
