package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue

internal class RoomPortalRescueSearch(
    private val sources: List<Int>,
    private val targets: List<Int>,
    private val bridges: List<Int>,
    private val positions: List<BlockPos>,
    private val range: Double,
    private val kind: EtherwarpKind,
    private val snapshot: BlockCache.SnapshotView,
    private val deadlineNanos: Long
) {
    data class Edge(val from: Int, val to: Int, val aim: Aim)
    data class Result(val edges: List<Edge>, val arrivals: Set<Int>, val attempts: Int)
    private data class State(val index: Int, val depth: Int, val estimate: Double, val sequence: Long)
    private data class ExpansionResult(val attempts: Int, val sequence: Long)

    private val rangeSq = range * range

    fun search(): Result {
        val targetSet = targets.toHashSet()
        val candidates = (bridges + targets).distinct()
        val depthByIndex = HashMap<Int, Int>()
        val queue = PriorityQueue(compareBy<State>({ it.estimate }, { it.depth }, { it.sequence }))
        var sequence = 0L
        sources.forEach {
            depthByIndex[it] = 0
            queue += State(it, 0, distanceToTarget(it), sequence++)
        }
        val edges = ArrayList<Edge>()
        val arrivals = LinkedHashSet<Int>()
        var attempts = 0
        while (queue.isNotEmpty() && attempts < MAX_ATTEMPTS && System.nanoTime() < deadlineNanos) {
            val current = queue.remove()
            if (depthByIndex[current.index] != current.depth) continue
            if (current.index in targetSet) {
                arrivals += current.index
                return Result(edges, arrivals, attempts)
            }
            if (current.depth >= MAX_DEPTH) continue
            val nextDepth = current.depth + 1
            val expanded = expandState(
                current.index, nextDepth, candidates, targetSet, attempts, depthByIndex, queue, edges, sequence
            )
            attempts = expanded.attempts
            sequence = expanded.sequence
        }
        return Result(edges, arrivals, attempts)
    }

    private fun expandState(
        current: Int,
        nextDepth: Int,
        candidates: List<Int>,
        targetSet: Set<Int>,
        attempts: Int,
        depthByIndex: MutableMap<Int, Int>,
        queue: PriorityQueue<State>,
        edges: MutableList<Edge>,
        sequence: Long
    ): ExpansionResult {
        var updatedAttempts = attempts
        var updatedSequence = sequence
        rescueTargets(current, candidates, targetSet).forEach { target ->
            if (updatedAttempts >= MAX_ATTEMPTS || System.nanoTime() >= deadlineNanos) return@forEach
            updatedAttempts++
            val aim = resolve(current, target) ?: return@forEach
            edges += Edge(current, target, aim)
            val previousDepth = depthByIndex[target]
            if (previousDepth == null || nextDepth < previousDepth) {
                depthByIndex[target] = nextDepth
                queue += State(target, nextDepth, nextDepth * rangeSq + distanceToTarget(target), updatedSequence++)
            }
        }
        return ExpansionResult(updatedAttempts, updatedSequence)
    }

    private fun rescueTargets(current: Int, candidates: List<Int>, targetSet: Set<Int>): List<Int> {
        val origin = eye(current)
        return candidates.asSequence()
            .filter { it != current && within(origin, positions[it - INDEX_OFFSET]) }
            .sortedWith(compareBy({ if (it in targetSet) 0 else 1 }, { VecUtils.distanceSq(positions[it - INDEX_OFFSET], positions[targets.first() - INDEX_OFFSET]) }))
            .take(MAX_BRANCHES)
            .toList()
    }

    private fun resolve(from: Int, to: Int): Aim? =
        ValidatedEdgeCache.resolve(positions[from - INDEX_OFFSET], positions[to - INDEX_OFFSET], range, kind, snapshot)

    private fun eye(index: Int): Vec3 {
        val position = positions[index - INDEX_OFFSET]
        return Vec3(position.x + CENTER, kind.landingY(position.y) + kind.eyeHeight(), position.z + CENTER)
    }

    private fun within(origin: Vec3, target: BlockPos): Boolean = VecUtils.centerDistanceSq(origin, target) <= rangeSq

    private fun distanceToTarget(index: Int): Double = VecUtils.distanceSq(
        positions[index - INDEX_OFFSET], positions[targets.first() - INDEX_OFFSET]
    )

    private companion object {
        private const val INDEX_OFFSET = 1
        private const val MAX_DEPTH = 4
        private const val MAX_BRANCHES = 16
        private const val MAX_ATTEMPTS = 48
        private const val CENTER = 0.5
    }
}
