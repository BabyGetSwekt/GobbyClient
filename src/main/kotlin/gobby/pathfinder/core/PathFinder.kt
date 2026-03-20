package gobby.pathfinder.core

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.pathfinder.movement.MovementSimulator
import gobby.pathfinder.world.BlockCache.StandSurface
import gobby.pathfinder.world.BlockCache
import net.minecraft.util.math.BlockPos
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

object PathFinder {
    private data class NodeKey(
        val pos: Long,
        val feetOffset: Int
    )

    var lastPath: List<PathNode>? = null

    private fun resolveStartSurface(start: BlockPos): StandSurface? {
        val player = mc.player ?: return BlockCache.resolveStandingSurface(start)
        val playerFeetY = player.y
        val searchMinY = playerFeetY - 1.5
        val searchMaxY = playerFeetY + BlockCache.STEP_HEIGHT

        val direct = BlockCache.resolveStandingSurface(start)
        val candidates = mutableListOf<StandSurface>()
        if (direct != null) candidates.add(direct)

        for (dx in -1..1) {
            for (dz in -1..1) {
                candidates += BlockCache.getStandableSurfaces(start.x + dx, start.z + dz, searchMinY, searchMaxY)
            }
        }

        return candidates
            .distinctBy { it.pos.asLong() to BlockCache.quantizeFeetOffset(it.pos, it.feetY) }
            .filter { candidate ->
                val horizontalDist = hypot((candidate.pos.x + 0.5) - player.x, (candidate.pos.z + 0.5) - player.z)
                horizontalDist <= 1.15 && abs(candidate.feetY - playerFeetY) <= 1.5
            }
            .minByOrNull { candidate ->
                val horizontalDist = hypot((candidate.pos.x + 0.5) - player.x, (candidate.pos.z + 0.5) - player.z)
                horizontalDist + abs(candidate.feetY - playerFeetY) * 1.5
            }
            ?: direct
    }

    fun findPath(start: BlockPos, goal: BlockPos, playerSpeed: Double, maxIterations: Int = 10000): List<PathNode>? {
        BlockCache.clear()

        val startSurface = resolveStartSurface(start) ?: run {
            lastPath = null
            return null
        }
        val goalSurface = BlockCache.resolveStandingSurface(goal)
        val goalFeetY = goalSurface?.feetY ?: goal.y.toDouble()
        val goalPos = goalSurface?.pos ?: goal

        val openQueue = PriorityQueue<PathNode>()
        val bestNodes = HashMap<NodeKey, PathNode>()
        val closedSet = HashSet<NodeKey>()

        fun key(pos: BlockPos, feetY: Double): NodeKey {
            return NodeKey(pos.asLong(), BlockCache.quantizeFeetOffset(pos, feetY))
        }

        fun heuristic(pos: BlockPos, feetY: Double): Double {
            val dx = (pos.x - goalPos.x).toDouble()
            val dy = feetY - goalFeetY
            val dz = (pos.z - goalPos.z).toDouble()
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        fun isGoal(node: PathNode): Boolean {
            if (node.pos != goalPos) return false
            return abs(node.feetY - goalFeetY) < 0.125
        }

        val startNode = PathNode(startSurface.pos, startSurface.feetY, null, 0.0, heuristic(startSurface.pos, startSurface.feetY))
        openQueue.add(startNode)
        bestNodes[key(startSurface.pos, startSurface.feetY)] = startNode

        var iterations = 0

        while (openQueue.isNotEmpty()) {
            if (iterations++ >= maxIterations) break

            val current = openQueue.poll()
            val currentKey = key(current.pos, current.feetY)

            val best = bestNodes[currentKey]
            if (best != null && best.g < current.g) continue

            if (isGoal(current)) {
                val path = smoothPath(current.reconstructPath())
                lastPath = path
                return path
            }

            closedSet.add(currentKey)

            for (neighbor in MovementSimulator.getNeighbors(current.pos, current.feetY, playerSpeed)) {
                val successorKey = key(neighbor.pos, neighbor.feetY)
                val successorCost = current.g + neighbor.cost

                val existing = bestNodes[successorKey]

                if (existing != null) {
                    if (existing.g <= successorCost) continue
                    closedSet.remove(successorKey)
                }

                val successorNode = PathNode(
                    neighbor.pos,
                    neighbor.feetY,
                    current,
                    successorCost,
                    existing?.h ?: heuristic(neighbor.pos, neighbor.feetY),
                    neighbor.action
                )

                bestNodes[successorKey] = successorNode
                openQueue.add(successorNode)
            }
        }

        lastPath = null
        return null
    }

    private fun smoothPath(raw: List<PathNode>): List<PathNode> {
        if (raw.size <= 2) return raw

        val result = mutableListOf(raw[0])
        var i = 1

        while (i < raw.size - 1) {
            val prev = result.last()
            val curr = raw[i]
            val next = raw[i + 1]

            if (curr.action == MoveAction.JUMP || next.action == MoveAction.JUMP) {
                result.add(curr)
                i++
                continue
            }

            if (abs(curr.feetY - prev.feetY) > 1.0E-3 || abs(next.feetY - curr.feetY) > 1.0E-3) {
                result.add(curr)
                i++
                continue
            }

            val dx1 = curr.pos.x - prev.pos.x
            val dz1 = curr.pos.z - prev.pos.z
            val dx2 = next.pos.x - curr.pos.x
            val dz2 = next.pos.z - curr.pos.z

            if (dx1 == dx2 && dz1 == dz2) {
                i++
                continue
            }

            result.add(curr)
            i++
        }

        result.add(raw.last())
        return result
    }
}
