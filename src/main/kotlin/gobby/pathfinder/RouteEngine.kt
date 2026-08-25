package gobby.pathfinder

import gobby.pathfinder.solver.VoxelGroundSolver
import gobby.pathfinder.solver.SkySolver
import gobby.pathfinder.world.BlockCache
import net.minecraft.world.phys.Vec3
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import gobby.utils.timer.Clock
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

enum class TravelMode { WALK, FLY, HYBRID }

object PlanStats {
    @Volatile var lastMeshMs: Long = 0

    @Volatile var lastSolveMs: Long = 0

    @Volatile var lastTotalMs: Long = 0

    @Volatile var lastPolygonCount: Int = 0

    @Volatile var lastWaypointCount: Int = 0
}

sealed class RoutePlan {
    abstract val waypoints: List<Vec3>
    abstract val isEmpty: Boolean

    data class Ground(override val waypoints: List<Vec3>, val complete: Boolean = true) : RoutePlan() {
        override val isEmpty: Boolean get() = waypoints.size < 2
    }

    data class Sky(override val waypoints: List<Vec3>) : RoutePlan() {
        override val isEmpty: Boolean get() = waypoints.size < 2
    }

    object Failed : RoutePlan() {
        override val waypoints: List<Vec3> = emptyList()
        override val isEmpty: Boolean = true
    }
}

object RouteEngine {

    private const val DEFAULT_SCAN_RANGE = 128

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "gobby-pathfinder").apply { isDaemon = true }
    }

    fun plan(start: Vec3, goal: Vec3, mode: TravelMode, scanRange: Int = DEFAULT_SCAN_RANGE): RoutePlan = when (mode) {
        TravelMode.WALK -> walk(start, goal)
        TravelMode.FLY -> fly(start, goal, scanRange)
        TravelMode.HYBRID -> hybrid(start, goal, scanRange)
    }

    fun planAsync(start: Vec3, goal: Vec3, mode: TravelMode, scanRange: Int = DEFAULT_SCAN_RANGE): CompletableFuture<RoutePlan> {
        val minX = (minOf(start.x, goal.x).toInt() - scanRange) shr 4
        val maxX = (maxOf(start.x, goal.x).toInt() + scanRange) shr 4
        val minZ = (minOf(start.z, goal.z).toInt() - scanRange) shr 4
        val maxZ = (maxOf(start.z, goal.z).toInt() + scanRange) shr 4
        BlockCache.captureLoadedChunks(minX, minZ, maxX, maxZ)
        return CompletableFuture.supplyAsync({ plan(start, goal, mode, scanRange) }, executor)
    }

    private const val SNAP_SEARCH_Y = 8.0
    private const val SNAP_SPIRAL_RADIUS = 4
    private const val GOAL_CLAMP_STEP = 8.0

    private fun snapToWalkableSurface(target: Vec3): Vec3? {
        val centerX = floor(target.x).toInt()
        val centerZ = floor(target.z).toInt()
        for (radius in 0..SNAP_SPIRAL_RADIUS) {
            val candidates = if (radius == 0) listOf(centerX to centerZ) else ringCells(centerX, centerZ, radius)
            findSurfaceIn(candidates, target)?.let { return it }
        }
        return null
    }

    private fun findSurfaceIn(candidates: List<Pair<Int, Int>>, target: Vec3): Vec3? = candidates.firstNotNullOfOrNull { (cx, cz) ->
        if (!BlockCache.isChunkAvailable(cx, cz)) return@firstNotNullOfOrNull null
        val surfaces = BlockCache.getStandableSurfaces(cx, cz, target.y - SNAP_SEARCH_Y, target.y + SNAP_SEARCH_Y)
        val best = surfaces.minByOrNull { abs(it.feetY - target.y) } ?: return@firstNotNullOfOrNull null
        Vec3(cx + 0.5, best.feetY, cz + 0.5)
    }

    private fun resolveReachableGoal(start: Vec3, goal: Vec3): Vec3? {
        snapToWalkableSurface(goal)?.let { return it }
        val toStart = start.subtract(goal)
        val dist = toStart.length()
        if (dist < GOAL_CLAMP_STEP) return null
        var clamped = GOAL_CLAMP_STEP
        while (clamped < dist) {
            val candidate = goal.add(toStart.scale(clamped / dist))
            snapToWalkableSurface(candidate)?.let { return it }
            clamped += GOAL_CLAMP_STEP
        }
        return null
    }

    private fun ringCells(cx: Int, cz: Int, r: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(8 * r)
        for (dx in -r..r) {
            out += (cx + dx) to (cz - r)
            out += (cx + dx) to (cz + r)
        }
        for (dz in -r + 1..r - 1) {
            out += (cx - r) to (cz + dz)
            out += (cx + r) to (cz + dz)
        }
        return out
    }

    private fun walk(start: Vec3, goal: Vec3): RoutePlan {
        val totalClock = Clock()
        val reachableGoal = resolveReachableGoal(start, goal) ?: run {
            resetWalkStats(totalClock)
            return RoutePlan.Failed
        }
        val solveClock = Clock()
        val result = VoxelGroundSolver.solve(start, reachableGoal)
        PlanStats.lastSolveMs = solveClock.getTime()
        PlanStats.lastMeshMs = 0
        PlanStats.lastPolygonCount = 0
        PlanStats.lastWaypointCount = result.waypoints.size
        PlanStats.lastTotalMs = totalClock.getTime()
        if (result.waypoints.size < 2) return RoutePlan.Failed
        val trulyUnreachable = !result.complete && result.exhausted && !result.frontierUnknown &&
            result.remainingDistance > UNREACHABLE_REMAINING_DIST
        if (trulyUnreachable) return RoutePlan.Failed
        return RoutePlan.Ground(result.waypoints, result.complete)
    }

    private fun resetWalkStats(totalClock: Clock) {
        PlanStats.lastSolveMs = 0
        PlanStats.lastMeshMs = 0
        PlanStats.lastPolygonCount = 0
        PlanStats.lastWaypointCount = 0
        PlanStats.lastTotalMs = totalClock.getTime()
    }

    private fun fly(start: Vec3, goal: Vec3, scanRange: Int): RoutePlan {
        val totalClock = Clock()
        val solveClock = Clock()
        val rawDist = abs(start.x - goal.x) + abs(start.y - goal.y) + abs(start.z - goal.z)
        val effectiveRange = max(scanRange.toDouble(), rawDist * 1.5 + 50).toInt()
        val waypoints = SkySolver.solve(start, goal, effectiveRange)
        PlanStats.lastMeshMs = 0
        PlanStats.lastSolveMs = solveClock.getTime()
        PlanStats.lastTotalMs = totalClock.getTime()
        PlanStats.lastPolygonCount = waypoints.size
        PlanStats.lastWaypointCount = waypoints.size
        return if (waypoints.size < 2) RoutePlan.Failed else RoutePlan.Sky(waypoints)
    }

    private fun hybrid(start: Vec3, goal: Vec3, scanRange: Int): RoutePlan {
        val ground = walk(start, goal)
        if (ground !is RoutePlan.Failed) return ground
        return fly(start, goal, scanRange)
    }
}
