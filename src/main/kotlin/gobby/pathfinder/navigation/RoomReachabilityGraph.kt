package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.pathLog
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.ArrayDeque

internal class RoomReachabilityGraph(
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
    private val positions = collectPositions()
    private val indexByPosition = positions.withIndex().associate { it.value to it.index + START_INDEX }
    private val preparedEdges = if (rooms.size == SINGLE_ROOM_COUNT) rooms.first().outgoing else mergePreparedEdges()
    private val geometry = RoomGraphPositions(positions, indexByPosition, portals, kind, range, START_INDEX)
    private val edges = RoomGraphEdges(positions.size + START_INDEX, geometry, deadlineNanos, ::resolveAim)
    private val roomPositions = rooms.associate { it.canonical to geometry.roomIndices(it) }
    private var reachedByRoom: Map<String, Int> = emptyMap()
    private val lazilyExpanded = HashSet<Int>()
    private val allIndices = (START_INDEX until positions.size + START_INDEX).toList()
    private val validatedPreparedAims = PreparedEdgeValidationCache(range, kind, snapshot)

    fun search(): GraphProposal = PlannerMemoScope.current()?.let { searchInternal() }
        ?: PlannerMemoScope.with(PlannerMemo(), ::searchInternal)

    private fun searchInternal(): GraphProposal {
        if (rooms.isEmpty()) return empty("no-rooms")
        val ingressPrepared = prepareStart().isNotEmpty()
        val portalsPrepared = portals.map(::preparePortal)
        val goalIndex = indexByPosition[goal] ?: return empty("goal:index")
        val goalPrepared = prepareGoal()
        addRuntimeEdges()
        addLocalEdges()
        pathLog { AtlasGraphDiagnostics.describePreparation(portals, ingressPrepared, portalsPrepared, goalPrepared) }
        val route = if (ingressPrepared && portalsPrepared.all { it } && goalPrepared) breadthFirst(goalIndex) else null
        if (route != null) return preparedProposal(route)
        if (rooms.any { it.edges.isNotEmpty() || it.outgoing.isNotEmpty() }) RoomGraphBackboneSearch(start, goal, range, kind, snapshot, deadlineNanos, rooms, portals).search()?.let { return GraphProposal(it, positions.size, edges.attempted, edges.valid) }
        val recovered = RoomGraphRecoverySearch(start, goal, range, kind, snapshot, deadlineNanos, rooms, portals).search()
        return recovered?.let { GraphProposal(it, positions.size, edges.attempted, edges.valid) } ?: empty("disconnected")
    }

    private fun preparePortal(portal: PreparedPortal): Boolean = System.nanoTime() < deadlineNanos && connectPortal(portal).isNotEmpty()

    private fun collectPositions(): List<BlockPos> {
        val values = LinkedHashSet<BlockPos>()
        rooms.forEach { room ->
            values.addAll(room.positions)
            values.addAll(room.anchors)
            values.addAll(room.runtimeSeeds)
            values.addAll(room.liveConnectors)
        }
        portals.forEach { portal -> values.addAll(portal.candidates) }
        values += goal
        return values.toList()
    }

    private fun addRuntimeEdges() {
        rooms.filter(PreparedGraphRoom::runtimeBridge).forEach { room ->
            edges.connectNeighbourhood(geometry.roomIndices(room).take(MAX_RUNTIME_CANDIDATES), MAX_NEIGHBOUR_EDGES)
        }
    }

    private fun addLocalEdges() {
        rooms.filter { !it.runtimeBridge }.forEach { room ->
            val indices = geometry.roomIndices(room)
            val live = RoomConnectorSelection.forRoom(room, portals).mapNotNull(indexByPosition::get)
                .distinct()
                .take(MAX_LOCAL_CANDIDATES)
            if (live.isEmpty()) return@forEach
            val prepared = live.map { geometry.positionOf(it) }.flatMap { connector ->
                geometry.nearest(indices, connector, MAX_LOCAL_CANDIDATES)
            }.distinct().take(MAX_LOCAL_CANDIDATES)
            edges.connectPairs(live, prepared, MAX_LOCAL_CONNECTORS)
            edges.connectPairs(prepared, live, MAX_LOCAL_CONNECTORS)
        }
    }

    private fun prepareStart(): Set<Int> {
        val room = rooms.first()
        val standingBlock = kind.standingBlock(start)
        val targets = geometry.nearestPositions(room, start, MAX_START_CANDIDATES)
        val reached = LinkedHashSet<Int>()
        val eye = Vec3(start.x, start.y + kind.eyeHeight(), start.z)
        targets.forEach { target ->
            if (reached.size >= MAX_VALID_CONNECTORS) return@forEach
            if (System.nanoTime() >= deadlineNanos) return@forEach
            val index = indexByPosition[target] ?: return@forEach
            val aim = if (target == standingBlock) Aim(ZERO_AIM, ZERO_AIM) else kind.aimAt(eye, target, range, snapshot = snapshot)
            aim?.let { resolved ->
                edges.addExternal(START_INDEX - 1, index, resolved)
                reached += index
            }
        }
        return reached
    }

    private fun connectPortal(portal: PreparedPortal): Set<Int> {
        val sourceRoom = roomPositions[portal.fromCanonical].orEmpty()
        val targetRoom = roomPositions[portal.toCanonical].orEmpty()
        val sources = rescueCandidates(sourceRoom, portal.fromSeed)
        val targets = rescueCandidates(targetRoom, portal.toSeed)
        if (sources.isEmpty() || targets.isEmpty()) return emptySet()
        val direct = edges.connectPairs(sources, targets, MAX_VALID_CONNECTORS)
        if (direct.isNotEmpty()) return direct
        val rescue = RoomPortalRescueSearch(sources, targets, portal.candidates.mapNotNull(indexByPosition::get), positions, range, kind, snapshot, deadlineNanos).search()
        edges.countAttempts(rescue.attempts)
        rescue.edges.forEach { edge -> edges.addExternal(edge.from, edge.to, edge.aim) }
        return rescue.arrivals
    }

    private fun rescueCandidates(indices: List<Int>, reference: BlockPos): List<Int> {
        val nearby = geometry.nearest(indices, reference, MAX_SIDE_CANDIDATES)
        if (nearby.isNotEmpty()) return nearby
        return indices.asSequence().take(MAX_RESCUE_CANDIDATES).toList()
    }

    private fun prepareGoal(): Boolean {
        val room = rooms.last()
        val goalIndex = indexByPosition[goal] ?: return false
        if (edges.reachesAny(goalIndex)) return true
        val sources = geometry.nearestPositions(room, goal, MAX_GOAL_CANDIDATES)
        val targets = ArrayList<Int>(sources.size)
        sources.forEach { source -> indexByPosition[source]?.let(targets::add) }
        var accepted = 0
        targets.forEach { source ->
            if (accepted >= MAX_VALID_CONNECTORS) return@forEach
            if (System.nanoTime() >= deadlineNanos) return@forEach
            if (!geometry.withinRange(source, goalIndex)) return@forEach
            if (!edges.connect(source, goalIndex)) return@forEach
            accepted++
        }
        return accepted > 0
    }

    private fun breadthFirst(goalIndex: Int): List<EtherwarpNode>? {
        val parent = HashMap<Int, Parent>()
        val queue = ArrayDeque<Int>().apply { add(START_INDEX - 1) }
        val visited = HashSet<Int>().apply { add(START_INDEX - 1) }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == goalIndex) return reconstruct(parent, goalIndex)
            connectGoalFrom(current, goalIndex, visited)
            val discovered = visited.size
            visitPreparedEdges(current, visited, parent, queue)
            visitRuntimeEdges(current, visited, parent, queue)
            if (visited.size == discovered && expandOnDemand(current, visited)) visitRuntimeEdges(current, visited, parent, queue)
        }
        reachedByRoom = rooms.associate { room -> room.label to geometry.roomIndices(room).count(visited::contains) }
        return null
    }

    private fun connectGoalFrom(current: Int, goalIndex: Int, visited: Set<Int>) {
        if (current < START_INDEX || goalIndex in visited || System.nanoTime() >= deadlineNanos) return
        if (!geometry.withinRange(current, goalIndex) || edges.of(current).any { it.to == goalIndex }) return
        edges.connect(current, goalIndex)
    }

    private fun expandOnDemand(current: Int, visited: Set<Int>): Boolean {
        if (current < START_INDEX || System.nanoTime() >= deadlineNanos) return false
        if (!lazilyExpanded.add(current)) return false
        var added = false
        geometry.nearest(allIndices.filterNot(visited::contains), geometry.positionOf(current), MAX_LAZY_NEIGHBOURS).forEach { target ->
            if (target == current || System.nanoTime() >= deadlineNanos) return@forEach
            if (!edges.connect(current, target)) return@forEach
            added = true
        }
        return added
    }

    private fun visitPreparedEdges(current: Int, visited: MutableSet<Int>, parent: MutableMap<Int, Parent>, queue: ArrayDeque<Int>) {
        val position = positionOf(current)
        preparedEdges[position].orEmpty().forEach { edge ->
            val target = indexByPosition[edge.to] ?: return@forEach
            val aim = validatedPreparedAims.resolve(edge) ?: return@forEach
            if (visited.add(target)) {
                parent[target] = Parent(current, aim)
                queue.addLast(target)
            }
        }
    }

    private fun visitRuntimeEdges(current: Int, visited: MutableSet<Int>, parent: MutableMap<Int, Parent>, queue: ArrayDeque<Int>) {
        edges.of(current).forEach { edge ->
            if (visited.add(edge.to)) {
                parent[edge.to] = Parent(current, edge.aim)
                queue.addLast(edge.to)
            }
        }
    }

    private fun positionOf(index: Int): BlockPos =
        if (index == START_INDEX - 1) kind.standingBlock(start) else geometry.positionOf(index)

    private fun mergePreparedEdges(): Map<BlockPos, List<PreparedDirectedEdge>> =
        rooms.asSequence().flatMap { it.outgoing.entries.asSequence() }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, groups) -> groups.flatten() }

    private fun resolveAim(sourceIndex: Int, target: BlockPos): Aim? =
        ValidatedEdgeCache.resolve(positions[sourceIndex - START_INDEX], target, range, kind, snapshot)

    private fun reconstruct(parent: Map<Int, Parent>, goalIndex: Int): List<EtherwarpNode> {
        val path = generateSequence(goalIndex) { parent[it]?.from }.toList().asReversed()
        return RoomGraphRouteMaterializer(start, kind, positions, START_INDEX).materialize(parent.mapValues { it.value.aim }, path)
    }

    private fun empty(stop: String) = GraphProposal(emptyList(), positions.size, edges.attempted, edges.valid, "$stop reached=$reachedByRoom")

    private fun preparedProposal(route: List<EtherwarpNode>): GraphProposal = rooms.none { it.runtimeBridge || it.liveConnectors.isNotEmpty() }.let { prepared -> GraphProposal(route, positions.size, edges.attempted, edges.valid, completenessCertified = prepared, compatiblePreparedRoute = prepared) }
    private companion object {
        private const val START_INDEX = 1; private const val SINGLE_ROOM_COUNT = 1; private const val ZERO_AIM = 0f
        private const val CENTER = 0.5; private const val MAX_SIDE_CANDIDATES = 160; private const val MAX_RUNTIME_CANDIDATES = 160
        private const val MAX_LOCAL_CANDIDATES = 12; private const val MAX_START_CANDIDATES = 160; private const val MAX_GOAL_CANDIDATES = 24
        private const val MAX_VALID_CONNECTORS = 2; private const val MAX_LOCAL_CONNECTORS = 160
        private const val MAX_RESCUE_CANDIDATES = 64; private const val MAX_LAZY_NEIGHBOURS = 12
        private const val MAX_NEIGHBOUR_EDGES = 6
    }
}
