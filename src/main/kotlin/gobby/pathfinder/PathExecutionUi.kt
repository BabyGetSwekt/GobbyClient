package gobby.pathfinder

import gobby.pathfinder.movement.InputManager
import gobby.utils.ChatUtils.modMessage
import kotlin.random.Random

internal class PathMicroPauser {
    private var remaining: Int = 0
    private var cooldown: Int = 0

    fun reset() {
        remaining = 0
        cooldown = MICRO_PAUSE_COOLDOWN
    }

    fun tick(enabled: Boolean): Boolean {
        if (!enabled) return false
        if (remaining > 0) {
            remaining--
            InputManager.releaseAll()
            return true
        }
        if (cooldown > 0) {
            cooldown--
            return false
        }
        if (Random.nextDouble() < MICRO_PAUSE_CHANCE) {
            remaining = Random.nextInt(MICRO_PAUSE_MIN_TICKS, MICRO_PAUSE_MAX_TICKS + 1)
            cooldown = MICRO_PAUSE_COOLDOWN
        }
        return false
    }
}

internal object PathRouteReporter {
    fun reportTimings(plan: RoutePlan) {
        val pieces = buildList {
            add("total=${PlanStats.lastTotalMs}ms")
            if (PlanStats.lastMeshMs > 0) add("mesh=${PlanStats.lastMeshMs}ms (${PlanStats.lastPolygonCount} polys)")
            if (PlanStats.lastSolveMs > 0) add("solve=${PlanStats.lastSolveMs}ms")
        }
        modMessage("Route found (${plan.waypoints.size} waypoints) - ${pieces.joinToString(" | ")}.")
    }
}
