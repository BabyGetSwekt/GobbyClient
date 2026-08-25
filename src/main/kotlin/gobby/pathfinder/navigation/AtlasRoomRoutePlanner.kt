package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.etherwarp.RoomStep
import gobby.pathfinder.etherwarp.SearchDeadline
import gobby.pathfinder.etherwarp.pathLog
import gobby.pathfinder.etherwarp.EtherwarpRouteValidator
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object AtlasRoomRoutePlanner {
    fun find(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline
    ): List<EtherwarpNode>? = if (deadline.expired) null else findValidated(from, goal, kind, config, rooms, grid, snapshot, deadline)?.nodes

    fun findPrepared(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        room: RoomStep,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView
    ): DependencyValidatedRoute? = fastAtlasRoute(from, goal, kind, config, listOf(room), grid, snapshot)

    fun findValidated(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline
    ): DependencyValidatedRoute? {
        if (kind != EtherwarpKind.ETHERWARP || rooms.isEmpty() || deadline.expired) return null
        AtlasRoomQueryCache.get(from, goal, kind, config, rooms, grid, snapshot)?.let { return it }
        fastAtlasRoute(from, goal, kind, config, rooms, grid, snapshot)?.let { return it }
        return findPreparedRoute(from, goal, kind, config, rooms, grid, snapshot, deadline)?.also {
            AtlasRoomQueryCache.put(from, goal, kind, config, rooms, grid, snapshot, it)
        }
    }

    private fun findPreparedRoute(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline
    ): DependencyValidatedRoute? {
        val cached = AtlasRoomPreparationCache.get(rooms, grid, snapshot)
        if (cached != null) return findPreparedGraphRoute(
            from, goal, kind, config, snapshot, deadline, cached.rooms, cached.portals, null, grid
        )
        return findUncachedPreparedRoute(from, goal, kind, config, rooms, grid, snapshot, deadline)
    }

    private fun findUncachedPreparedRoute(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline
    ): DependencyValidatedRoute? {
        val preparer = AtlasRoomPreparer(grid, snapshot, deadline)
        val preparation = preparer.prepare(rooms) ?: return null
        AtlasRoomPreparationCache.put(rooms, grid, snapshot, preparation)
        return findPreparedGraphRoute(from, goal, kind, config, snapshot, deadline, preparation.rooms, preparation.portals, preparer.runtimeIndex, grid)
    }

    private fun findPreparedGraphRoute(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline,
        prepared: List<PreparedGraphRoom>,
        portals: List<PreparedPortal>,
        runtimeIndex: RoomLandingIndex?,
        grid: Array<MapTile>
    ): DependencyValidatedRoute? {
        val connected = RouteEndpointConnector.add(prepared, from, goal, kind)
        RuntimeBridgeRoutePlanner.find(from, goal, kind.searchRange(config), kind, snapshot, connected, portals, System.nanoTime() + deadline.remainingNanos)?.validatedRoute?.let { return it }
        connected.singleOrNull()?.let { room ->
            directPreparedRoute(from, goal, kind, config, room, snapshot)?.let { return it }
        }
        val initial = RoomReachabilityGraph(
            from,
            goal,
            kind.searchRange(config),
            kind,
            snapshot,
            System.nanoTime() + deadline.remainingNanos,
            connected,
            portals
        ).search()
        initial.validatedRoute?.let { return it }
        pathLog { AtlasGraphDiagnostics.describeGraph(prepared, portals, initial) }
        val proposal = if (initial.route.isNotEmpty() || deadline.expired) initial else AtlasExpandedProposal.find(
            from, goal, kind, config, snapshot, deadline, connected, portals,
            runtimeIndex ?: AtlasRoomPreparer(grid, snapshot, deadline).runtimeIndex, grid
        )
        if (proposal.route.isEmpty()) return null
        return EtherwarpRouteValidator.validate(proposal.route, kind.searchRange(config), kind, snapshot)?.copy(
            completenessCertified = proposal.completenessCertified,
            compatiblePreparedRoute = proposal.compatiblePreparedRoute
        )
    }

    private fun fastAtlasRoute(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView
    ): DependencyValidatedRoute? {
        val step = rooms.singleOrNull() ?: return null
        val room = AtlasRoomPreparer.preparedAtlasRoom(step, grid, snapshot) ?: return null
        return directPreparedRoute(from, goal, kind, config, room, snapshot)
    }

    private fun directPreparedRoute(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        room: PreparedGraphRoom,
        snapshot: BlockCache.SnapshotView
    ): DependencyValidatedRoute? {
        val source = preparedSource(from, room) ?: return null
        val edge = room.outgoing[source].orEmpty().firstOrNull { it.to == goal } ?: return null
        val start = EtherwarpNode(from.x, from.y, from.z, source, ZERO_SCORE, ZERO_SCORE, null, edge.yaw, edge.pitch)
        val target = EtherwarpNode(goal.x + CENTER, kind.landingY(goal.y), goal.z + CENTER, goal, kind.hopCost(goal), ZERO_SCORE, null, ZERO_AIM, ZERO_AIM)
        return EtherwarpRouteValidator.validate(listOf(start, target), kind.searchRange(config), kind, snapshot)?.copy(
            compatiblePreparedRoute = true
        )
    }

    private fun preparedSource(from: Vec3, room: PreparedGraphRoom): BlockPos? {
        val feet = BlockPos.containing(from)
        return sequenceOf(feet, feet.below()).firstOrNull { it in room.outgoing }
    }

    private const val CENTER = 0.5
    private const val ZERO_SCORE = 0.0
    private const val ZERO_AIM = 0f
}
