package gobby.pathfinder.solver

import net.minecraft.world.phys.Vec3

internal object VoxelPathReconstruction {
    fun build(end: VoxelGroundSolver.Entry): List<Vec3> {
        val points = ArrayList<Vec3>()
        var current: VoxelGroundSolver.Entry? = end
        while (current != null) {
            val node = current.node
            points += Vec3(node.x + 0.5, node.feetY, node.z + 0.5)
            current = current.parent
        }
        return points.asReversed()
    }
}
