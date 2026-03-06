package gobby.pathfinder.core

import gobby.pathfinder.movement.InputManager.MoveAction
import net.minecraft.util.math.BlockPos

class PathNode(
    val pos: BlockPos,
    val parent: PathNode?,
    val g: Double,
    val h: Double,
    val action: MoveAction? = null
) : Comparable<PathNode> {

    val f: Double get() = g + h

    override fun compareTo(other: PathNode): Int = f.compareTo(other.f)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathNode) return false
        return pos == other.pos
    }

    override fun hashCode(): Int = pos.hashCode()

    fun reconstructPath(): List<PathNode> {
        val path = mutableListOf<PathNode>()
        var current: PathNode? = this
        while (current != null) {
            path.add(current)
            current = current.parent
        }
        return path.reversed()
    }
}
