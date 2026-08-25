package gobby.pathfinder.etherwarp

import gobby.features.dungeons.DungeonMap
import gobby.pathfinder.navigation.PreparedDungeonRouteCache
import gobby.pathfinder.navigation.DirectRouteCache
import gobby.pathfinder.navigation.AtlasRoomRoutePlanner
import gobby.pathfinder.navigation.DungeonLandingBlacklist
import gobby.pathfinder.navigation.DungeonRoomCoordinates
import gobby.pathfinder.navigation.RoomGraphCache
import gobby.pathfinder.navigation.PlannerMemo
import gobby.pathfinder.navigation.PlannerMemoScope
import gobby.pathfinder.search.SearchLane
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.map.RoomGraph
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3

object DungeonEtherwarpPathfinder {
    private val searchTimedOut = ThreadLocal.withInitial { false }
    var lastSearchTimedOut: Boolean
        get() = searchTimedOut.get()
        private set(value) { searchTimedOut.set(value) }
    private const val ATLAS_BUDGET_SHARE = 0.15
    private const val FIELD_WAIT_FACTOR = 2L
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val DIRECT_ATTEMPT_BUDGET_SHARE = 0.1
    private const val GRAPH_BUDGET_MS = 2_000L

    fun preload(grid: Array<MapTile>, mapRevision: Long) {
        PreparedDungeonRouteCache.preload()
        RoomGraphCache.preload(grid, mapRevision)
    }

    fun findDungeonPath(
        from: Vec3,
        to: BlockPos,
        kind: EtherwarpKind = EtherwarpKind.ETHERWARP,
        config: EtherwarpPathConfig = EtherwarpPathConfig(),
        enterRoom: Boolean = true,
        grid: Array<MapTile> = DungeonMap.grid,
        opened: (Int) -> Boolean = DungeonMap::isDoorOpened, snapshot: BlockCache.SnapshotView? = null,
        mapRevision: Long = DungeonMap.revision, deadline: SearchDeadline? = null
    ): List<EtherwarpNode>? = PathPlanDiagnostics.withPlan {
        lastSearchTimedOut = false
        val setup = prepareDungeonSearch(from, to, kind, config, enterRoom, grid, snapshot, mapRevision, deadline) {
            lastSearchTimedOut = true
        } ?: return@withPlan null
        searchPreparedPath(setup, grid, opened).also { route ->
            pathLog { "plan result hops=${route?.let { it.size - 1 } ?: -1} timedOut=$lastSearchTimedOut ${PathPlanDiagnostics.describeBudget(setup.deadline)}" }
        }
    }

    private fun searchPreparedPath(setup: DungeonSearchSetup, grid: Array<MapTile>, opened: (Int) -> Boolean): List<EtherwarpNode>? {
        val context = DungeonPathSearchContext(setup.from, setup.goalBlock, setup.kind, setup.config, setup.enterRoom, setup.cache, setup.deadline, setup.fallbackDeadline, setup.goalCell, setup.mapRevision, setup.exactGoal, DungeonLandingBlacklist.forGrid(grid))
        cachedRoute(setup, context)?.let { return it }
        if (hasCachedFailure(setup)) {
            pathLog { "cache preparedRoute failure hit -> abort" }
            return context.fail()
        }
        if (setup.enterRoom) {
            EtherwarpPathfinder.exactDirect(setup.from, setup.goalBlock, setup.kind, setup.config, DungeonSegmentGoals.goalReached(setup.goalBlock, emptySet()), setup.cache)?.let {
                context.completeDirect(it)?.let { route ->
                    pathLog { "shortcut exactDirect hops=${route.size - 1}" }
                    return route
                }
            }
        }
        if (setup.enterRoom) DungeonSameRoomRoute.find(setup.from, setup.goalBlock, setup.kind, setup.config, setup.startCell, setup.goalCell, grid, setup.cache)
            ?.takeIf { !setup.exactGoal || it.lastOrNull()?.pos == setup.goalBlock }
            ?.takeUnless(context::landsOnBlacklistedTile)
            ?.let {
                pathLog { "shortcut sameRoom hops=${it.size - 1}" }
                return it
            }
        setup.startCell?.let { (grid.getOrNull(it) as? MapTile.Room)?.data }?.takeIf(::canHaveCustomEgress)?.let { DungeonCustomEgressRoute.find(setup.from, setup.goalBlock, setup.kind, setup.config, setup.enterRoom, setup.startCell, setup.goalCell, grid, opened, setup.cache, setup.mapRevision, setup.deadline)
            ?.takeUnless(context::landsOnBlacklistedTile)?.let {
                pathLog { "shortcut customEgress hops=${it.size - 1}" }
                return it
            } }
        val graph = RoomGraphCache.get(grid, setup.mapRevision)
        val search = { findFromGraph(context, setup.startCell, setup.goalCell, setup.target, grid, opened, graph) }
        return PlannerMemoScope.current()?.let { search() } ?: PlannerMemoScope.with(PlannerMemo(), search)
    }

    private fun cachedRoute(setup: DungeonSearchSetup, context: DungeonPathSearchContext): List<EtherwarpNode>? {
        val targetCell = setup.goalCell ?: return null
        DirectRouteCache.take(setup.from, setup.goalBlock, targetCell, setup.enterRoom, setup.kind, setup.config, setup.cache, setup.mapRevision)
            ?.takeUnless(context::landsOnBlacklistedTile)?.let {
                pathLog { "cache directRoute hit hops=${it.size - 1}" }
                return it
            }
        return PreparedDungeonRouteCache.take(setup.from, setup.goalBlock, targetCell, setup.enterRoom, setup.kind, setup.config, setup.cache, setup.mapRevision)
            ?.takeUnless(context::landsOnBlacklistedTile)?.also {
                pathLog { "cache preparedRoute hit hops=${it.size - 1}" }
            }
    }

    private fun hasCachedFailure(setup: DungeonSearchSetup): Boolean = setup.goalCell?.let { targetCell ->
        PreparedDungeonRouteCache.hasFailure(setup.from, setup.goalBlock, targetCell, setup.enterRoom, setup.kind, setup.config, setup.cache, setup.mapRevision)
    } == true

    private fun findFromGraph(context: DungeonPathSearchContext, startCell: Int?, goalCell: Int?, target: BlockPos, grid: Array<MapTile>, opened: (Int) -> Boolean, graph: RoomGraph): List<EtherwarpNode>? {
        val goalRoom = goalCell?.let(graph::canonical)
        val goalCells = goalRoom?.let(graph::component).orEmpty()
        val startRoom = startCell?.let(graph::canonical)
        pathLog { "plan from=${PathPlanDiagnostics.describeVec(context.from)} startCell=$startCell startRoom=$startRoom to=${PathPlanDiagnostics.describeBlock(target)} goalBlock=${PathPlanDiagnostics.describeBlock(context.goalBlock)} goalCell=$goalCell goalRoom=$goalRoom goalCells=${goalCells.size} enterRoom=${context.enterRoom} exactGoal=${context.exactGoal} range=${context.kind.searchRange(context.config)} ${PathPlanDiagnostics.describeBudget(context.deadline)}" }
        if (startCell == null || goalCell == null || startRoom == null || goalRoom == null || startRoom == goalRoom) {
            return findDegenerate(context, startRoom, goalRoom, goalCells, startCell, goalCell)
        }
        val rooms = DungeonRoomPathfinder.findPath(grid, graph, startCell, goalCell, opened = opened)
        if (rooms == null) {
            pathLog { "room graph has no path from $startCell to $goalCell" }
            return context.fail()
        }
        return findAcrossRooms(context, rooms, graph, goalCells, grid)
    }

    private fun findDegenerate(context: DungeonPathSearchContext, startRoom: Int?, goalRoom: Int?, goalCells: Set<Int>, startCell: Int?, goalCell: Int?): List<EtherwarpNode>? {
        if (context.enterRoom && !context.exactGoal && startRoom != null && goalRoom != null) EtherwarpHopField.forGoal(context.goalBlock, context.kind.searchRange(context.config))?.query(context.from, context.kind.searchRange(context.config))?.let { return context.complete(it) }
        val reached = if (context.exactGoal || startRoom != null && startRoom == goalRoom) {
            { position: BlockPos -> position == context.goalBlock }
        } else DungeonSegmentGoals.goalReached(context.goalBlock, goalCells)
        val route = context.complete(singleSegment(context.from, context.goalBlock, context.kind, context.config, reached, context.cache, context.deadline, landingFilter = context.landingFilter))
            ?: context.fail()
        pathLog { "degenerate startCell=$startCell goalCell=$goalCell startRoom=$startRoom goalRoom=$goalRoom sameRoom=${startRoom == goalRoom} -> ${route?.let { "ok(${it.size})" } ?: "FAILED"}" }
        return route
    }

    private fun findAcrossRooms(context: DungeonPathSearchContext, rooms: List<RoomStep>, graph: RoomGraph, goalCells: Set<Int>, grid: Array<MapTile>): List<EtherwarpNode>? {
        val allowedCells = rooms.asSequence().flatMap { graph.component(it.cellIndex).asSequence() }.toSet()
        preparedFieldRoute(context, allowedCells)?.let { return it }
        requestHopField(context, allowedCells, grid)
        val atlasRoute = atlasRoute(context, rooms, grid)
        val lowerBound = EtherwarpHopLowerBound.forRoute(context.from, context.goalBlock, context.kind.searchRange(context.config))
        atlasRoute?.takeIf { context.exactGoal || it.completenessCertified || it.compatiblePreparedRoute || it.nodes.lastIndex <= lowerBound }?.let {
            return DungeonAtlasRouteAcceptance.accept(it, context, allowedCells, grid)
        }
        findRoomFallback(context, rooms, graph, goalCells, grid)?.let { return it }
        atlasRoute?.let { return DungeonAtlasRouteAcceptance.accept(it, context, allowedCells, grid) }
        return preparedFieldRoute(context, allowedCells, fieldWaitNanos(context))
    }

    private fun atlasRoute(context: DungeonPathSearchContext, rooms: List<RoomStep>, grid: Array<MapTile>) =
        AtlasRoomRoutePlanner.findValidated(context.from, context.goalBlock, context.kind, context.config, rooms, grid, context.cache, context.deadline.slice(ATLAS_BUDGET_SHARE, GRAPH_BUDGET_MS))
            ?.takeIf { !context.exactGoal || it.nodes.lastOrNull()?.pos == context.goalBlock }
            ?.takeUnless { context.landsOnBlacklistedTile(it.nodes) }
            .also { pathLog { "atlas route ${it?.let { route -> "hops=${route.nodes.size - 1} certified=${route.completenessCertified}" } ?: "unavailable"} ${PathPlanDiagnostics.describeBudget(context.deadline)}" } }

    private fun fieldWaitNanos(context: DungeonPathSearchContext): Long =
        context.config.timeout * FIELD_WAIT_FACTOR * NANOS_PER_MILLISECOND

    private fun preparedFieldRoute(context: DungeonPathSearchContext, allowedCells: Set<Int>, awaitNanos: Long = 0L): List<EtherwarpNode>? {
        if (!context.enterRoom || context.exactGoal) return null
        val range = context.kind.searchRange(context.config)
        val field = EtherwarpHopField.forGoal(context.goalBlock, range, allowedCells)
            ?: EtherwarpHopField.awaitForGoal(context.goalBlock, range, allowedCells, awaitNanos)
        val route = field?.query(context.from, range)
        pathLog { "hop field ${if (field == null) "absent" else route?.let { "hit hops=${it.size - 1}" } ?: "built but no visible node"} ${PathPlanDiagnostics.describeBudget(context.deadline)}" }
        return route?.let { context.complete(it) }
    }

    private fun findRoomFallback(context: DungeonPathSearchContext, rooms: List<RoomStep>, graph: RoomGraph, goalCells: Set<Int>, grid: Array<MapTile>): List<EtherwarpNode>? {
        logRoomPath(rooms, grid, context.cache)
        val reached = DungeonSegmentGoals.goalReached(context.goalBlock, goalCells, context.exactGoal)
        if (context.enterRoom) {
            val directDeadline = context.deadline.slice(DIRECT_ATTEMPT_BUDGET_SHARE)
            val direct = singleSegment(context.from, context.goalBlock, context.kind, context.config, reached, context.cache, directDeadline, SearchLane.PRIMITIVE_ONLY, context.landingFilter)
            pathLog { "direct attempt ${direct?.let { "ok(${it.size}) skipped stitch" } ?: "failed"} ${PathPlanDiagnostics.describeBudget(context.deadline)}" }
            direct?.let { return context.complete(it) }
        }
        DungeonRouteStitcher.stitch(context, rooms, graph, goalCells, grid, context.fallbackDeadline)?.let {
            pathLog { "stitch ok(${it.size})" }
            return context.complete(it)
        }
        if (context.enterRoom) singleSegment(context.from, context.goalBlock, context.kind, context.config, reached, context.cache, context.fallbackDeadline, landingFilter = context.landingFilter)?.let {
            pathLog { "unrestricted direct ok(${it.size})" }
            return context.complete(it)
        }
        lastSearchTimedOut = context.fallbackDeadline.expired && !Thread.currentThread().isInterrupted
        pathLog { "stitch FAILED -> abort timedOut=$lastSearchTimedOut ${PathPlanDiagnostics.describeBudget(context.deadline)}" }
        return context.fail()
    }

    private fun logRoomPath(rooms: List<RoomStep>, grid: Array<MapTile>, cache: BlockCache.SnapshotView) {
        if (!PathPlanDiagnostics.enabled) return
        pathLog { "room path (${rooms.size}): ${rooms.joinToString(" -> ") { "${it.data.name}${it.doorCell?.let { cell -> "[${PathPlanDiagnostics.describeDoor(cell, grid, doorOpen(cell) { position -> cache.getBlockState(position) })}]" } ?: ""}" }}" }
        rooms.forEach { step -> pathLog { "  room ${PathPlanDiagnostics.describeRoom(step.cellIndex, grid, cache)}" } }
    }

    private fun requestHopField(context: DungeonPathSearchContext, allowedCells: Set<Int>, grid: Array<MapTile>) {
        if (context.kind != EtherwarpKind.ETHERWARP) return
        EtherwarpHopField.request(context.goalBlock, context.kind.searchRange(context.config), context.cache, allowedCells, grid)
    }

    fun hopFieldFor(to: BlockPos, kind: EtherwarpKind, config: EtherwarpPathConfig): EtherwarpHopField.Handle? =
        EtherwarpHopField.forGoal(kind.goal(to), kind.searchRange(config))

    fun doorOpen(doorCell: Int, stateAt: (BlockPos) -> BlockState?): Boolean =
        stateAt(doorSamplePos(doorCell))?.isAir == true

    private fun doorSamplePos(doorCell: Int): BlockPos =
        BlockPos(MapGrid.worldX(MapGrid.col(doorCell)), DungeonRoomCoordinates.DOOR_BLOCK_SAMPLE_Y, MapGrid.worldZ(MapGrid.row(doorCell)))

    private fun singleSegment(from: Vec3, goalBlock: BlockPos, kind: EtherwarpKind, config: EtherwarpPathConfig, reachGoal: (BlockPos) -> Boolean, cache: BlockCache.SnapshotView, deadline: SearchDeadline, lane: SearchLane = SearchLane.HYBRID, landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL): List<EtherwarpNode>? =
        EtherwarpPathfinder.search(from, goalBlock, kind, config, reachGoal, cache, deadline, lane, landingFilter)?.let { EtherwarpPathfinder.smooth(it, kind.searchRange(config), kind, cache) }
}
