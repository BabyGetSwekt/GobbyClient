package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.LandingKey
import gobby.pathfinder.navigation.PreparedSegmentCache
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.map.RoomGraph
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

internal object DungeonRouteStitcher {

    fun stitch(
        context: DungeonPathSearchContext,
        rooms: List<RoomStep>,
        graph: RoomGraph,
        goalCells: Set<Int>,
        grid: Array<MapTile>,
        deadline: SearchDeadline
    ): List<EtherwarpNode>? {
        val full = mutableListOf<EtherwarpNode>()
        var origin = context.from
        val steps = if (context.enterRoom || rooms.size < 2) rooms.size else rooms.size - 1
        for (index in 0 until steps) {
            if (Thread.currentThread().isInterrupted) return null
            val allowEnter = context.enterRoom || index < steps - 1
            val goal = tightenIfAlreadyInGoalRoom(
                DungeonSegmentGoals.resolve(
                    rooms[index], rooms.getOrNull(index + 1), graph, grid, context.cache,
                    context.goalBlock, goalCells, context.exactGoal, allowEnter
                ),
                full, context.goalBlock
            )
            val segmentDeadline = deadline.child(segmentBudget(deadline, steps - index - 1))
            val segment = searchSegment(context, origin, goal, segmentDeadline, full.lastOrNull()?.let(LandingKey::from))
            if (segment == null) {
                logFailure(index, rooms, goal, origin, allowEnter, deadline)
                return null
            }
            if (!append(full, segment)) {
                pathLog { "  segment $index discontinuous at ${PathPlanDiagnostics.describeVec(origin)}" }
                return null
            }
            val landing = full.lastOrNull() ?: return null
            origin = landing.eye
            pathLog { "  segment $index ok hops=${segment.size - 1} landed=${PathPlanDiagnostics.describeBlock(landing.pos)} ${PathPlanDiagnostics.describeBudget(deadline)}" }
            if (landing.pos == context.goalBlock) break
        }
        return EtherwarpPathfinder.smooth(full, context.kind.searchRange(context.config), context.kind, context.cache)
    }

    internal fun tightenIfAlreadyInGoalRoom(
        goal: DungeonSegmentGoal,
        route: List<EtherwarpNode>,
        goalBlock: BlockPos
    ): DungeonSegmentGoal =
        if (goal.goalKind != SegmentGoalKind.FINAL_GOAL || route.lastOrNull()?.pos?.let(goal.reached) != true) goal
        else DungeonSegmentGoal(goal.target, goal.goalKind, goal.door) { it == goalBlock }

    internal fun segmentBudget(deadline: SearchDeadline, remainingSegments: Int): Long =
        (deadline.remainingMillis - MIN_SEGMENT_BUDGET_MS * remainingSegments).coerceAtLeast(MIN_SEGMENT_BUDGET_MS)

    private fun searchSegment(
        context: DungeonPathSearchContext,
        origin: Vec3,
        goal: DungeonSegmentGoal,
        deadline: SearchDeadline,
        stableOrigin: LandingKey?
    ): List<EtherwarpNode>? = PreparedSegmentCache.takeOrCompute(
        stableOrigin, goal.target, context.enterRoom, context.kind, context.config, context.mapRevision, context.cache,
        compute = { DungeonSegmentSearch.find(origin, goal.target, context.kind, context.config, goal.reached, context.cache, deadline, context.landingFilter) },
        cacheFailure = { !deadline.expired && !Thread.currentThread().isInterrupted }
    )

    private fun logFailure(
        index: Int,
        rooms: List<RoomStep>,
        goal: DungeonSegmentGoal,
        origin: Vec3,
        allowEnter: Boolean,
        deadline: SearchDeadline
    ) = pathLog {
        "  segment $index FAILED ${rooms[index].data.name} -> ${rooms.getOrNull(index + 1)?.data?.name ?: "goal"}" +
            " origin=${PathPlanDiagnostics.describeVec(origin)} target=${PathPlanDiagnostics.describeBlock(goal.target)}" +
            " goalKind=${goal.goalKind} door=${PathPlanDiagnostics.describeBlock(goal.door)} allowEnter=$allowEnter" +
            " ${PathPlanDiagnostics.describeBudget(deadline)}"
    }

    fun append(route: MutableList<EtherwarpNode>, segment: List<EtherwarpNode>): Boolean {
        if (segment.size < MIN_SEGMENT_NODES) return false
        if (route.isEmpty()) {
            route.addAll(segment)
            return true
        }
        val boundary = route.last()
        val segmentStart = segment.first()
        if (!samePosition(boundary.eye, segmentStart.eye)) return false
        boundary.yaw = segmentStart.yaw
        boundary.pitch = segmentStart.pitch
        route.addAll(segment.drop(FIRST_SEGMENT_NODE))
        return true
    }

    private fun samePosition(first: Vec3, second: Vec3): Boolean =
        abs(first.x - second.x) <= POSITION_EPSILON &&
            abs(first.y - second.y) <= POSITION_EPSILON &&
            abs(first.z - second.z) <= POSITION_EPSILON

    private const val MIN_SEGMENT_BUDGET_MS = 150L
    private const val MIN_SEGMENT_NODES = 2
    private const val FIRST_SEGMENT_NODE = 1
    private const val POSITION_EPSILON = 1.0E-6
}
