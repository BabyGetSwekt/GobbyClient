package gobby.pathfinder.core

import gobby.pathfinder.movement.InputManager.MoveAction
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
                val path = smoothPath(current.reconstructPath())
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

            if (curr.pos.y != prev.pos.y || next.pos.y != curr.pos.y) {
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
