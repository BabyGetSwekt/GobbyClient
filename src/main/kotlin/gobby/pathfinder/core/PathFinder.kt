package gobby.pathfinder.core

import gobby.pathfinder.movement.MovementSimulator
import gobby.pathfinder.world.BlockCache
import net.minecraft.util.math.BlockPos
import java.util.PriorityQueue
import kotlin.math.sqrt

object PathFinder {

    var lastPath: List<PathNode>? = null

    fun findPath(start: BlockPos, goal: BlockPos, playerSpeed: Double, maxIterations: Int = 10000): List<PathNode>? {
        BlockCache.clear()

        val openQueue = PriorityQueue<PathNode>()
        val bestNodes = HashMap<Long, PathNode>()
        val closedSet = HashSet<Long>()

        fun heuristic(pos: BlockPos): Double {
            val dx = (pos.x - goal.x).toDouble()
            val dy = (pos.y - goal.y).toDouble()
            val dz = (pos.z - goal.z).toDouble()
            return sqrt(dx * dx + dy * dy + dz * dz)
        }

        val startNode = PathNode(start, null, 0.0, heuristic(start))
        openQueue.add(startNode)
        bestNodes[start.asLong()] = startNode

        var iterations = 0

        while (openQueue.isNotEmpty()) {
            if (iterations++ >= maxIterations) break

            val current = openQueue.poll()
            val currentKey = current.pos.asLong()

            val best = bestNodes[currentKey]
            if (best != null && best.g < current.g) continue

            if (current.pos == goal) {
                val path = current.reconstructPath()
                lastPath = path
                return path
            }

            closedSet.add(currentKey)

            for (neighbor in MovementSimulator.getNeighbors(current.pos, playerSpeed)) {
                val successorKey = neighbor.pos.asLong()
                val successorCost = current.g + neighbor.cost

                val existing = bestNodes[successorKey]

                if (existing != null) {
                    if (existing.g <= successorCost) continue
                    closedSet.remove(successorKey)
                }

                val successorNode = PathNode(
                    neighbor.pos,
                    current,
                    successorCost,
                    existing?.h ?: heuristic(neighbor.pos),
                    neighbor.action
                )

                bestNodes[successorKey] = successorNode
                openQueue.add(successorNode)
            }
        }

        lastPath = null
        return null
    }
}
