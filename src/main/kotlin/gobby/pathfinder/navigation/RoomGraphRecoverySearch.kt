package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.hypot

internal class RoomGraphRecoverySearch(
    private val start: Vec3,
    private val goal: BlockPos,
    private val range: Double,
    private val kind: EtherwarpKind,
    private val snapshot: BlockCache.SnapshotView,
    private val deadlineNanos: Long,
    rooms: List<PreparedGraphRoom>,
    private val portals: List<PreparedPortal>
) {
    private data class OpenNode(val index: Int, val hops: Int, val estimate: Double, val sequence: Long)
    private data class Parent(val from: Int, val aim: Aim)

    private val positions = collectPositions(rooms, portals)
    private val roomMembership = buildRoomMembership(rooms, portals)
    private val portalTargetsBySource = portals
        .flatMap { portal -> portal.fromCandidates.map { source -> source to portal.toCandidates } }
        .groupBy({ it.first }, { it.second })
    private val rangeSq = range * range

    fun search(): List<EtherwarpNode>? {
        if (positions.isEmpty()) return null
        val goalIndex = positions.indexOf(goal) + START_INDEX
        val costs = IntArray(positions.size + START_INDEX) { Int.MAX_VALUE }
        val parents = HashMap<Int, Parent>()
        val open = PriorityQueue(compareBy<OpenNode>({ it.hops + it.estimate }, { it.sequence }))
        var sequence = 0L
        costs[START_INDEX - 1] = 0
        open += OpenNode(START_INDEX - 1, 0, estimate(start), sequence++)
        while (open.isNotEmpty() && System.nanoTime() < deadlineNanos) {
            val current = open.remove()
            if (current.hops != costs[current.index]) continue
            if (current.index == goalIndex) return reconstruct(parents, goalIndex)
            sequence = expandState(current, candidateTargets(current.index), costs, parents, open, sequence)
        }
        return null
    }

    private fun expandState(
        current: OpenNode,
        targets: List<Int>,
        costs: IntArray,
        parents: MutableMap<Int, Parent>,
        open: PriorityQueue<OpenNode>,
        sequence: Long
    ): Long {
        var updatedSequence = sequence
        targets.forEach { target ->
            val nextHops = current.hops + 1
            if (nextHops >= costs[target]) return@forEach
            val aim = resolveAim(current.index, target) ?: return@forEach
            costs[target] = nextHops
            parents[target] = Parent(current.index, aim)
            open += OpenNode(target, nextHops, estimate(positionOf(target)), updatedSequence++)
        }
        return updatedSequence
    }

    private fun candidateTargets(index: Int): List<Int> {
        val origin = eyeOf(index)
        return positions.indices.asSequence()
            .map { it + START_INDEX }
            .filter { it != index && canTransition(index, it) && withinRange(origin, positionOf(it)) }
            .sortedBy { VecUtils.distanceSq(positionOf(it), goal) }
            .take(MAX_NEIGHBORS)
            .toList()
    }

    private fun resolveAim(from: Int, to: Int): Aim? {
        if (from == START_INDEX - 1) {
            return kind.aimAt(eyeOf(from), positionOf(to), range, snapshot = snapshot)
        }
        return ValidatedEdgeCache.resolve(positionOf(from), positionOf(to), range, kind, snapshot)
    }

    private fun reconstruct(parents: Map<Int, Parent>, goalIndex: Int): List<EtherwarpNode>? {
        val indices = generateSequence(goalIndex) { parents[it]?.from }.toList().asReversed()
        if (indices.size < MIN_ROUTE_NODES || indices.first() != START_INDEX - 1) return null
        return indices.mapIndexed { index, value ->
            val position = positionOf(value)
            val aim = indices.getOrNull(index + 1)?.let { parents[it]?.aim } ?: Aim(ZERO_AIM, ZERO_AIM)
            if (value == START_INDEX - 1) {
                EtherwarpNode(start.x, start.y, start.z, position, ZERO_SCORE, ZERO_SCORE, null, aim.yaw, aim.pitch)
            } else {
                EtherwarpNode(position.x + CENTER, kind.landingY(position.y), position.z + CENTER, position, index.toDouble(), ZERO_SCORE, null, aim.yaw, aim.pitch)
            }
        }.also { route ->
            route.last().yaw = ZERO_AIM
            route.last().pitch = ZERO_AIM
        }
    }

    private fun collectPositions(rooms: List<PreparedGraphRoom>, portals: List<PreparedPortal>): List<BlockPos> =
        (rooms.flatMap { it.positions + it.anchors + it.runtimeSeeds + it.liveConnectors } + portals.flatMap { it.candidates } + goal).distinct()

    private fun positionOf(index: Int): BlockPos = if (index == START_INDEX - 1) BlockPos.containing(start) else positions[index - START_INDEX]

    private fun eyeOf(index: Int): Vec3 = if (index == START_INDEX - 1) {
        start.add(0.0, kind.eyeHeight(), 0.0)
    } else {
        val position = positionOf(index)
        Vec3(position.x + CENTER, kind.landingY(position.y) + kind.eyeHeight(), position.z + CENTER)
    }

    private fun estimate(position: Vec3): Double = hypot(goal.x + CENTER - position.x, goal.z + CENTER - position.z) / range

    private fun estimate(position: BlockPos): Double = estimate(Vec3(position.x + CENTER, kind.landingY(position.y), position.z + CENTER))

    private fun withinRange(origin: Vec3, target: BlockPos): Boolean = VecUtils.centerDistanceSq(origin, target) <= rangeSq

    private fun canTransition(from: Int, to: Int): Boolean {
        val fromPosition = positionOf(from)
        val targetPosition = positionOf(to)
        val fromRooms = roomMembership[fromPosition].orEmpty()
        val targetRooms = roomMembership[targetPosition].orEmpty()
        if (fromRooms.any(targetRooms::contains)) return true
        return portalTargetsBySource[fromPosition].orEmpty().any { targetPosition in it }
    }

    private fun buildRoomMembership(rooms: List<PreparedGraphRoom>, portals: List<PreparedPortal>): Map<BlockPos, Set<Int>> {
        val membership = HashMap<BlockPos, MutableSet<Int>>()
        rooms.forEach { room ->
            recordMembership(membership, room.canonical, room.positions + room.anchors + room.runtimeSeeds + room.liveConnectors)
        }
        portals.forEach { portal ->
            recordMembership(membership, portal.fromCanonical, portal.fromCandidates)
            recordMembership(membership, portal.toCanonical, portal.toCandidates)
        }
        rooms.firstOrNull()?.let { recordMembership(membership, it.canonical, listOf(BlockPos.containing(start))) }
        rooms.lastOrNull()?.let { recordMembership(membership, it.canonical, listOf(goal)) }
        return membership
    }

    private fun recordMembership(membership: MutableMap<BlockPos, MutableSet<Int>>, canonical: Int, positions: List<BlockPos>) {
        positions.forEach { position -> membership.getOrPut(position) { LinkedHashSet() } += canonical }
    }

    private companion object {
        private const val START_INDEX = 1
        private const val MIN_ROUTE_NODES = 2
        private const val MAX_NEIGHBORS = 24
        private const val CENTER = 0.5
        private const val ZERO_SCORE = 0.0
        private const val ZERO_AIM = 0f
    }
}
