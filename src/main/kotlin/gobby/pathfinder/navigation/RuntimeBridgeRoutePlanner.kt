package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object RuntimeBridgeRoutePlanner {
    fun find(
        start: Vec3,
        goal: BlockPos,
        range: Double,
        kind: EtherwarpKind,
        snapshot: BlockCache.SnapshotView,
        rooms: List<PreparedGraphRoom>,
        portals: List<PreparedPortal>,
        deadlineNanos: Long
    ): GraphProposal? {
        if (rooms.isEmpty() || portals.size + ROOM_TRANSITION_OFFSET != rooms.size) return null
        val edges = ArrayList<Edge>()
        var current = BlockPos.containing(start)
        val first = firstEdge(start, current, rooms.first(), range, kind, snapshot, deadlineNanos) ?: return null
        edges += first
        current = first.target
        for (index in portals.indices) {
            val bridge = bridgeEdges(current, rooms[index], rooms[index + 1], portals[index], range, kind, snapshot, deadlineNanos) ?: return null
            edges.addAll(bridge)
            current = bridge.last().target
        }
        if (current != goal) edges += edge(current, goal, range, kind, snapshot, deadlineNanos) ?: return null
        val route = nodes(start, edges, kind)
        val dependencies = captureDependencies(route, snapshot) ?: return null
        return GraphProposal(
            route,
            edges.size + NODE_COUNT_OFFSET,
            edges.size,
            edges.size,
            validatedRoute = DependencyValidatedRoute(route, dependencies, compatiblePreparedRoute = true),
            compatiblePreparedRoute = true
        )
    }

    private fun firstEdge(
        start: Vec3,
        current: BlockPos,
        room: PreparedGraphRoom,
        range: Double,
        kind: EtherwarpKind,
        snapshot: BlockCache.SnapshotView,
        deadlineNanos: Long
    ): Edge? = candidates(room).asSequence()
        .filter { System.nanoTime() < deadlineNanos }
        .mapNotNull { target ->
            kind.aimAt(Vec3(start.x, start.y + kind.eyeHeight(), start.z), target, range, snapshot = snapshot)
                ?.let { Edge(current, target, it) }
        }
        .firstOrNull()

    private fun bridgeEdges(
        current: BlockPos,
        fromRoom: PreparedGraphRoom,
        toRoom: PreparedGraphRoom,
        portal: PreparedPortal,
        range: Double,
        kind: EtherwarpKind,
        snapshot: BlockCache.SnapshotView,
        deadlineNanos: Long
    ): List<Edge>? {
        val sources = sourceApproaches(current, portal.fromCandidates + candidates(fromRoom), range, kind, snapshot, deadlineNanos)
        val targets = (portal.toCandidates + candidates(toRoom)).distinct()
        if (sources.isEmpty() || targets.isEmpty()) return null
        val pairCount = sources.size * targets.size
        for (pair in 0 until pairCount) {
            if (System.nanoTime() >= deadlineNanos) return null
            val approach = sources[pair / targets.size]
            val target = targets[pair % targets.size]
            val departure = edge(approach.source, target, range, kind, snapshot, deadlineNanos) ?: continue
            return listOfNotNull(approach.edge, departure)
        }
        return null
    }

    private fun sourceApproaches(
        current: BlockPos,
        sources: List<BlockPos>,
        range: Double,
        kind: EtherwarpKind,
        snapshot: BlockCache.SnapshotView,
        deadlineNanos: Long
    ): List<Approach> = sources.distinct().asSequence().mapNotNull { source ->
        if (System.nanoTime() >= deadlineNanos) return@mapNotNull null
        if (source == current) return@mapNotNull Approach(source, null)
        edge(current, source, range, kind, snapshot, deadlineNanos)?.let { Approach(source, it) }
    }.toList()

    private fun edge(
        source: BlockPos,
        target: BlockPos,
        range: Double,
        kind: EtherwarpKind,
        snapshot: BlockCache.SnapshotView,
        deadlineNanos: Long
    ): Edge? {
        if (System.nanoTime() >= deadlineNanos || source == target) return null
        return ValidatedEdgeCache.resolve(source, target, range, kind, snapshot)?.let { Edge(source, target, it) }
    }

    private fun candidates(room: PreparedGraphRoom): List<BlockPos> =
        (room.positions + room.anchors + room.liveConnectors).distinct()

    private fun captureDependencies(route: List<EtherwarpNode>, snapshot: BlockCache.SnapshotView): List<EdgeDependency>? {
        if (snapshot.isDirectSnapshot) return emptyList()
        val tracked = snapshot.isolatedTrackingReads()
        val dependencies = route.zipWithNext().mapNotNull { (from, to) -> EdgeDependency.capture(from, to, tracked) }
        snapshot.mergeAccessedReads(tracked)
        return dependencies.takeIf { it.size == route.lastIndex && tracked.missingChunksAccessed().isEmpty() }
    }

    private fun nodes(start: Vec3, edges: List<Edge>, kind: EtherwarpKind): List<EtherwarpNode> {
        val result = ArrayList<EtherwarpNode>(edges.size + 1)
        result += EtherwarpNode(start.x, start.y, start.z, BlockPos.containing(start), 0.0, 0.0, null, edges.first().aim.yaw, edges.first().aim.pitch)
        for (index in FIRST_TRANSITION_EDGE_INDEX until edges.size) {
            val edge = edges[index]
            result += EtherwarpNode(edge.source.x + CENTER, kind.landingY(edge.source.y), edge.source.z + CENTER, edge.source, index.toDouble(), 0.0, null, edge.aim.yaw, edge.aim.pitch)
        }
        val last = edges.last()
        result += EtherwarpNode(last.target.x + CENTER, kind.landingY(last.target.y), last.target.z + CENTER, last.target, edges.size.toDouble(), 0.0, null, ZERO_AIM, ZERO_AIM)
        return result
    }

    private data class Edge(val source: BlockPos, val target: BlockPos, val aim: Aim)
    private data class Approach(val source: BlockPos, val edge: Edge?)

    private const val CENTER = 0.5
    private const val ZERO_AIM = 0f
    private const val ROOM_TRANSITION_OFFSET = 1
    private const val NODE_COUNT_OFFSET = 1
    private const val FIRST_TRANSITION_EDGE_INDEX = 1
}
