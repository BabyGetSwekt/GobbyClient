package gobby.pathfinder.core

import gobby.pathfinder.movement.InputManager.MoveAction
import net.minecraft.util.math.BlockPos

class PathNode(
    val pos: BlockPos,
    val feetY: Double,
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
        return pos == other.pos && kotlin.math.abs(feetY - other.feetY) < 1.0E-4
    }

    override fun hashCode(): Int = 31 * pos.hashCode() + (feetY * 16.0).toInt()

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
