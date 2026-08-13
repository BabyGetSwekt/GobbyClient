package gobby.pathfinder.etherwarp

import gobby.utils.skyblock.EtherwarpWorldAccess
import net.minecraft.world.phys.Vec3

class EtherwarpGoalField internal constructor(
    private val graph: EtherwarpGraph,
    private val next: IntArray,
    private val distance: IntArray
) {
    companion object {
        internal const val UNREACHED = -1
    }

    fun reaches(index: Int): Boolean = distance[index] != UNREACHED

    fun hopsFrom(index: Int): Int = distance[index]

    fun pathFrom(from: Vec3, access: EtherwarpWorldAccess): List<EtherwarpNode>? {
        val entry = graph.bestEntry(from, access) { reaches(it) } ?: return null
        return graph.toNodes(from, generateSequence(entry) { current -> next[current].takeIf { it != UNREACHED } }.toList())
    }
}
