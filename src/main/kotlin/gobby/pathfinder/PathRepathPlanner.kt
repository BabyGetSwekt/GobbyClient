package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import net.minecraft.world.phys.Vec3

internal object PathRepathPlanner {
    fun submit(start: Vec3, goal: Vec3, mode: TravelMode, onSuccess: (RoutePlan) -> Unit, onFailure: () -> Unit) {
        RouteEngine.planAsync(start, goal, mode).thenAccept { routePlan ->
            mc.execute {
                if (routePlan is RoutePlan.Failed) onFailure() else onSuccess(routePlan)
            }
        }
    }
}
