package gobby.pathfinder.movement

import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.pathfinder.world.BlockCache
import net.minecraft.util.math.BlockPos

data class Neighbor(
    val pos: BlockPos,
    val action: MoveAction,
    val cost: Double
)

object MovementSimulator {

    const val BASE_WALK_SPEED = 0.1

    private const val CARDINAL_COST = 1.0
    private const val DIAGONAL_COST = 1.414
    private const val JUMP_COST = 1.5
    private const val FALL_BASE_COST = 1.0
    private const val FALL_PER_BLOCK = 0.3
    private const val MAX_FALL_SCAN = 40

    private val CARDINAL_DIRS = arrayOf(
        intArrayOf(0, 1),
        intArrayOf(0, -1),
        intArrayOf(-1, 0),
        intArrayOf(1, 0)
    )

    private val DIAGONAL_DIRS = arrayOf(
        intArrayOf(1, 1),
        intArrayOf(-1, 1),
        intArrayOf(1, -1),
        intArrayOf(-1, -1)
    )

    private fun findLanding(columnPos: BlockPos): BlockPos? {
        for (dy in 1..MAX_FALL_SCAN) {
            val feetPos = columnPos.down(dy)
            if (BlockCache.isSolid(feetPos.down())) {
                if (BlockCache.isPassable(feetPos) && BlockCache.isPassable(feetPos.up())) {
                    return feetPos
                }
                return null
            }
        }
        return null
    }

    fun getNeighbors(pos: BlockPos, playerSpeed: Double): List<Neighbor> {
        val speedFactor = playerSpeed / BASE_WALK_SPEED
        val neighbors = mutableListOf<Neighbor>()

        for (dir in CARDINAL_DIRS) {
            val dx = dir[0]
            val dz = dir[1]

            val flatTarget = pos.add(dx, 0, dz)
            if (BlockCache.isWalkable(flatTarget)) {
                neighbors.add(Neighbor(flatTarget, MoveAction.FORWARD, CARDINAL_COST / speedFactor))
            }

            val jumpTarget = pos.add(dx, 1, dz)
            if (BlockCache.isPassable(pos.up(2)) && BlockCache.isWalkable(jumpTarget)) {
                neighbors.add(Neighbor(jumpTarget, MoveAction.JUMP, JUMP_COST / speedFactor))
            }

            if (BlockCache.isPassable(flatTarget) && BlockCache.isPassable(flatTarget.up())) {
                val landing = findLanding(flatTarget)
                if (landing != null) {
                    val fallDist = pos.y - landing.y
                    val cost = (FALL_BASE_COST + fallDist * FALL_PER_BLOCK) / speedFactor
                    neighbors.add(Neighbor(landing, MoveAction.FORWARD, cost))
                }
            }
        }

        for (dir in DIAGONAL_DIRS) {
            val dx = dir[0]
            val dz = dir[1]

            val adj1 = pos.add(dx, 0, 0)
            val adj2 = pos.add(0, 0, dz)
            if (!BlockCache.isWalkable(adj1) && !BlockCache.isWalkable(adj2)) continue

            val diagTarget = pos.add(dx, 0, dz)
            if (BlockCache.isWalkable(diagTarget)) {
                if (BlockCache.isPassable(adj1) && BlockCache.isPassable(adj1.up())
                    && BlockCache.isPassable(adj2) && BlockCache.isPassable(adj2.up())) {
                    neighbors.add(Neighbor(diagTarget, MoveAction.FORWARD, DIAGONAL_COST / speedFactor))
                }
            }
        }

        return neighbors
    }
}
