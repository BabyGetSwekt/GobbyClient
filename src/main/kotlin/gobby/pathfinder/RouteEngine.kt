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
import kotlin.math.sqrt

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
        TravelMode.WALK -> walk(start, goal, scanRange)
        TravelMode.FLY -> fly(start, goal, scanRange)
        TravelMode.HYBRID -> hybrid(start, goal, scanRange)
    }

    fun planAsync(start: Vec3, goal: Vec3, mode: TravelMode, scanRange: Int = DEFAULT_SCAN_RANGE): CompletableFuture<RoutePlan> =
        CompletableFuture.supplyAsync({ plan(start, goal, mode, scanRange) }, executor)

    fun planSegmentAsync(
        start: Vec3,
        finalGoal: Vec3,
        segmentBlocks: Int,
        mode: TravelMode
    ): CompletableFuture<SegmentResult> = CompletableFuture.supplyAsync({
        val rawIntermediate = intermediateGoal(start, finalGoal, segmentBlocks)
        val isFinal = rawIntermediate === finalGoal
        val snapped = if (mode == TravelMode.FLY) rawIntermediate else snapToWalkableSurface(rawIntermediate) ?: rawIntermediate
        val resultPlan = plan(start, snapped, mode, segmentBlocks + 20)
        SegmentResult(resultPlan, isFinal)
    }, executor)

    private fun intermediateGoal(start: Vec3, goal: Vec3, segmentBlocks: Int): Vec3 {
        val dx = goal.x - start.x
        val dz = goal.z - start.z
        val hDist = sqrt(dx * dx + dz * dz)
        if (hDist <= segmentBlocks * 1.3) return goal
        val ratio = segmentBlocks / hDist
        val dy = goal.y - start.y
        return Vec3(start.x + dx * ratio, start.y + dy * ratio, start.z + dz * ratio)
    }

    private fun snapToWalkableSurface(target: Vec3): Vec3? {
        val maxSearchY = 8.0
        val maxLateralSpiral = 4
        val centerX = floor(target.x).toInt()
        val centerZ = floor(target.z).toInt()
        for (radius in 0..maxLateralSpiral) {
            val candidates = if (radius == 0) listOf(centerX to centerZ) else ringCells(centerX, centerZ, radius)
            for ((cx, cz) in candidates) {
                if (!BlockCache.isChunkLoaded(cx, cz)) continue
                val surfaces = BlockCache.getStandableSurfaces(cx, cz, target.y - maxSearchY, target.y + maxSearchY)
                val best = surfaces.minByOrNull { abs(it.feetY - target.y) } ?: continue
                return Vec3(cx + 0.5, best.feetY, cz + 0.5)
            }
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

    data class SegmentResult(val plan: RoutePlan, val isFinal: Boolean)

    private fun walk(start: Vec3, goal: Vec3, scanRange: Int): RoutePlan {
        val totalClock = Clock()
        val goalChunkLoaded = BlockCache.isChunkLoaded(floor(goal.x).toInt(), floor(goal.z).toInt())
        val snappedGoal = snapToWalkableSurface(goal)
        if (snappedGoal == null && goalChunkLoaded) return RoutePlan.Failed
        val solveClock = Clock()
        val result = VoxelGroundSolver.solve(start, snappedGoal ?: goal)
        PlanStats.lastSolveMs = solveClock.getTime()
        PlanStats.lastMeshMs = 0
        PlanStats.lastPolygonCount = 0
        PlanStats.lastWaypointCount = result.waypoints.size
        PlanStats.lastTotalMs = totalClock.getTime()
        if (result.waypoints.size < 2) return RoutePlan.Failed
        if (!result.complete && goalChunkLoaded && result.remainingDistance > UNREACHABLE_REMAINING_DIST) return RoutePlan.Failed
        return RoutePlan.Ground(result.waypoints, result.complete)
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
        val ground = walk(start, goal, scanRange)
        if (ground !is RoutePlan.Failed) return ground
        return fly(start, goal, scanRange)
    }
}
