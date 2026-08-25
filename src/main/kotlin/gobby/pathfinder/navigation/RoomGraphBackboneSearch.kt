package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque

internal class RoomGraphBackboneSearch(
    private val start: Vec3,
    private val goal: BlockPos,
    private val range: Double,
    private val kind: EtherwarpKind,
    private val snapshot: BlockCache.SnapshotView,
    private val deadlineNanos: Long,
    private val rooms: List<PreparedGraphRoom>,
    private val portals: List<PreparedPortal>
) {
    private data class Parent(val from: Int, val aim: Aim)
    private data class PreparedTarget(val index: Int, val edge: PreparedDirectedEdge?)

    private val positions = collectPositions()
    private val indexByPosition = positions.withIndex().associate { it.value to it.index + POSITION_INDEX }
    private val memberships = buildMembership()
    private val roomTargetIndices = rooms.associate { room ->
        room.canonical to roomTargetPositions(room).mapNotNull(indexByPosition::get).toSet()
    }
    private val portalsByFromPosition = portals
        .flatMap { portal -> portal.fromCandidates.map { it to portal } }
        .groupBy({ it.first }, { it.second })
    private val prepared = rooms.asSequence()
        .flatMap { room -> (room.edges + room.outgoing.values.flatten()).asSequence() }
        .groupBy(PreparedDirectedEdge::from)
    private val rangeSq = range * range

    fun search(): List<EtherwarpNode>? {
        if (rooms.isEmpty() || positions.isEmpty()) return null
        val parents = HashMap<Int, Parent>()
        val queue = ArrayDeque<Int>().apply { add(START_NODE_INDEX) }
        val visited = HashSet<Int>().apply { add(START_NODE_INDEX) }
        while (queue.isNotEmpty() && System.nanoTime() < deadlineNanos) {
            val current = queue.removeFirst()
            if (current == goalIndex()) return reconstruct(parents, current)
            expand(current, visited, parents, queue)
        }
        return null
    }

    private fun expand(current: Int, visited: MutableSet<Int>, parents: MutableMap<Int, Parent>, queue: ArrayDeque<Int>) {
        targets(current).forEach { target ->
            if (visited.contains(target.index)) return@forEach
            val aim = resolve(current, target) ?: return@forEach
            visited += target.index
            parents[target.index] = Parent(current, aim)
            queue.addLast(target.index)
        }
    }

    private fun resolve(current: Int, target: PreparedTarget): Aim? {
        if (current == START_NODE_INDEX) return kind.aimAt(startEye(), positions[target.index - POSITION_INDEX], range, snapshot = snapshot)
        val preparedEdge = target.edge
        if (preparedEdge != null) return ValidatedEdgeCache.resolvePrepared(preparedEdge, range, kind, snapshot)
        return ValidatedEdgeCache.resolve(positionOf(current), positions[target.index - POSITION_INDEX], range, kind, snapshot)
    }

    private fun targets(current: Int): List<PreparedTarget> {
        val origin = positionOf(current)
        val candidates = LinkedHashMap<Int, PreparedDirectedEdge?>()
        prepared[origin].orEmpty().forEach { edge -> indexByPosition[edge.to]?.let { candidates.putIfAbsent(it, edge) } }
        connectorTargets(current).forEach { index -> candidates.putIfAbsent(index, null) }
        return candidates.asSequence()
            .filter { it.key != current && canTransition(origin, positions[it.key - POSITION_INDEX]) }
            .filter { withinRange(current, it.key) }
            .sortedBy { VecUtils.distanceSq(positions[it.key - POSITION_INDEX], goal) }
            .take(MAX_TARGETS)
            .map { PreparedTarget(it.key, it.value) }
            .toList()
    }

    private fun connectorTargets(current: Int): Set<Int> {
        val origin = positionOf(current)
        val targets = LinkedHashSet<Int>()
        if (current == START_NODE_INDEX) rooms.firstOrNull()?.let { addRoomTargets(targets, it.canonical) }
        memberships[origin].orEmpty().forEach { canonical -> addRoomTargets(targets, canonical) }
        portalsByFromPosition[origin].orEmpty().forEach { addPortalTargets(targets, it) }
        if (memberships[origin].orEmpty().contains(rooms.lastOrNull()?.canonical)) indexByPosition[goal]?.let(targets::add)
        return targets
    }

    private fun addRoomTargets(targets: MutableSet<Int>, canonical: Int) {
        targets.addAll(roomTargetIndices[canonical].orEmpty())
    }

    private fun addPortalTargets(targets: MutableSet<Int>, portal: PreparedPortal) {
        portal.toCandidates.mapNotNull(indexByPosition::get).forEach(targets::add)
    }

    private fun roomPortalCandidates(room: PreparedGraphRoom): List<BlockPos> = portals.asSequence()
        .filter { it.fromCanonical == room.canonical || it.toCanonical == room.canonical }
        .flatMap { it.candidates.asSequence() }
        .toList()

    private fun canTransition(from: BlockPos, to: BlockPos): Boolean {
        val fromRooms = memberships[from].orEmpty()
        val toRooms = memberships[to].orEmpty()
        return fromRooms.any(toRooms::contains) || portalsByFromPosition[from].orEmpty().any { to in it.toCandidates }
    }

    private fun withinRange(from: Int, to: Int): Boolean {
        val origin = if (from == START_NODE_INDEX) startEye() else landingEye(positionOf(from))
        return VecUtils.centerDistanceSq(origin, positions[to - POSITION_INDEX]) <= rangeSq
    }

    private fun reconstruct(parents: Map<Int, Parent>, goalIndex: Int): List<EtherwarpNode>? {
        val indices = generateSequence(goalIndex) { parents[it]?.from }.toList().asReversed()
        if (indices.firstOrNull() != START_NODE_INDEX) return null
        return indices.mapIndexed { index, value ->
            val pos = positionOf(value)
            val aim = indices.getOrNull(index + 1)?.let { parents[it]?.aim } ?: Aim(ZERO_AIM, ZERO_AIM)
            if (value == START_NODE_INDEX) EtherwarpNode(start.x, start.y, start.z, BlockPos.containing(start), ZERO_SCORE, ZERO_SCORE, null, aim.yaw, aim.pitch)
            else EtherwarpNode(pos.x + CENTER, kind.landingY(pos.y), pos.z + CENTER, pos, index.toDouble(), ZERO_SCORE, null, aim.yaw, aim.pitch)
        }.also { route ->
            route.last().yaw = ZERO_AIM
            route.last().pitch = ZERO_AIM
        }
    }

    private fun collectPositions(): List<BlockPos> = LinkedHashSet<BlockPos>().apply {
        addAll(rooms.flatMap { it.positions + it.anchors + it.runtimeSeeds + it.liveConnectors })
        addAll(portals.flatMap(PreparedPortal::candidates))
        add(goal)
    }.toList()

    private fun buildMembership(): Map<BlockPos, Set<Int>> {
        val membership = HashMap<BlockPos, MutableSet<Int>>()
        rooms.forEach { addRoomMembership(membership, it) }
        portals.forEach { addPortalMembership(membership, it) }
        rooms.firstOrNull()?.let { membership.getOrPut(BlockPos.containing(start)) { LinkedHashSet() } += it.canonical }
        rooms.lastOrNull()?.let { membership.getOrPut(goal) { LinkedHashSet() } += it.canonical }
        return membership
    }

    private fun addRoomMembership(membership: MutableMap<BlockPos, MutableSet<Int>>, room: PreparedGraphRoom) {
        (room.positions + room.anchors + room.runtimeSeeds + room.liveConnectors).forEach { membership.getOrPut(it) { LinkedHashSet() } += room.canonical }
    }

    private fun addPortalMembership(membership: MutableMap<BlockPos, MutableSet<Int>>, portal: PreparedPortal) {
        portal.fromCandidates.forEach { membership.getOrPut(it) { LinkedHashSet() } += portal.fromCanonical }
        portal.toCandidates.forEach { membership.getOrPut(it) { LinkedHashSet() } += portal.toCanonical }
    }

    private fun roomTargetPositions(room: PreparedGraphRoom): Set<BlockPos> =
        (room.positions + room.anchors + room.runtimeSeeds + room.liveConnectors + roomPortalCandidates(room)).toSet()

    private fun goalIndex(): Int = indexByPosition[goal] ?: -1

    private fun positionOf(index: Int): BlockPos = if (index == START_NODE_INDEX) BlockPos.containing(start) else positions[index - POSITION_INDEX]

    private fun startEye(): Vec3 = Vec3(start.x, start.y + kind.eyeHeight(), start.z)

    private fun landingEye(pos: BlockPos): Vec3 = Vec3(pos.x + CENTER, kind.landingY(pos.y) + kind.eyeHeight(), pos.z + CENTER)

    private companion object {
        private const val START_NODE_INDEX = 0
        private const val POSITION_INDEX = 1
        private const val MAX_TARGETS = 24
        private const val CENTER = 0.5
        private const val ZERO_SCORE = 0.0
        private const val ZERO_AIM = 0f
    }
}
