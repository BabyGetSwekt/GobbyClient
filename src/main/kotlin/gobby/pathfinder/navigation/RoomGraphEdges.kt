package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import net.minecraft.core.BlockPos

internal class RoomGraphEdges(
    size: Int,
    private val positions: RoomGraphPositions,
    private val deadlineNanos: Long,
    private val resolveAim: (Int, BlockPos) -> Aim?
) {
    data class Edge(val to: Int, val aim: Aim)

    private val outgoing = Array(size) { ArrayList<Edge>() }
    var attempted = 0
        private set
    var valid = 0
        private set

    fun of(index: Int): List<Edge> = outgoing[index]

    fun reachesAny(target: Int): Boolean = outgoing.any { edges -> edges.any { it.to == target } }

    fun addExternal(from: Int, to: Int, aim: Aim) {
        outgoing[from] += Edge(to, aim)
        valid++
    }

    fun countAttempts(extra: Int) {
        attempted += extra
    }

    fun connect(from: Int, to: Int): Boolean {
        attempted++
        val aim = resolveAim(from, positions.positionOf(to)) ?: return false
        addExternal(from, to, aim)
        return true
    }

    fun connectPairs(sources: List<Int>, targets: List<Int>, arrivalLimit: Int): Set<Int> {
        if (sources.isEmpty() || targets.isEmpty()) return emptySet()
        val arrivals = LinkedHashSet<Int>()
        for (pair in 0 until sources.size * targets.size) {
            if (System.nanoTime() >= deadlineNanos) break
            val from = sources[pair / targets.size]
            val to = targets[pair % targets.size]
            if (!positions.withinRange(from, to) || !connect(from, to)) continue
            arrivals += to
            if (arrivals.size >= arrivalLimit) break
        }
        return arrivals
    }

    fun connectNeighbourhood(candidates: List<Int>, perSourceLimit: Int) {
        candidates.forEach { from ->
            if (System.nanoTime() >= deadlineNanos) return
            var connected = 0
            positions.nearest(candidates, positions.positionOf(from), perSourceLimit + 1).forEach { to ->
                if (to == from || connected >= perSourceLimit) return@forEach
                if (connect(from, to)) connected++
            }
        }
    }
}
