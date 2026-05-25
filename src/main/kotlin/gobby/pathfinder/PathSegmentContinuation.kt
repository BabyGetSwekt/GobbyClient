package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.modMessage
import net.minecraft.world.phys.Vec3

internal class PathSegmentContinuation {
    var segmentInFlight: Boolean = false
        private set
    var segmentedRoute: Boolean = false

    fun reset(enabled: Boolean, originalGoal: Vec3?, segmentEnd: Vec3) {
        segmentInFlight = false
        segmentedRoute = enabled && originalGoal != null && originalGoal.distanceTo(segmentEnd) > 1.5
    }

    fun shouldRequestNextSegment(cursor: Int, waypointCount: Int): Boolean {
        return segmentedRoute && !segmentInFlight && waypointCount > 0 &&
            cursor.toDouble() / waypointCount >= SEGMENT_PROGRESS_THRESHOLD
    }

    fun requestNextSegment(
        waypoints: MutableList<Vec3>,
        finalGoal: Vec3?,
        segmentBlocks: Int,
        travelMode: TravelMode,
        stop: () -> Unit,
        replanFromCurrentPosition: (Vec3) -> Unit
    ) {
        val goal = finalGoal ?: return
        if (waypoints.isEmpty()) return
        segmentInFlight = true
        RouteEngine.planSegmentAsync(waypoints.last(), goal, segmentBlocks, travelMode).thenAccept { result ->
            mc.execute { handleSegmentResult(result, waypoints, stop, replanFromCurrentPosition) }
        }
    }

    private fun handleSegmentResult(
        result: RouteEngine.SegmentResult,
        waypoints: MutableList<Vec3>,
        stop: () -> Unit,
        replanFromCurrentPosition: (Vec3) -> Unit
    ) {
        segmentInFlight = false
        val newPlan = result.plan
        if (newPlan is RoutePlan.Failed || newPlan.waypoints.isEmpty()) {
            modMessage(if (result.isFinal) "Final segment failed near goal." else "Segment failed, retrying full path...")
            if (result.isFinal) {
                stop()
                return
            }
            val curPos = mc.player?.position() ?: return
            replanFromCurrentPosition(curPos)
            return
        }
        for (i in 1 until newPlan.waypoints.size) waypoints += newPlan.waypoints[i]
        if (result.isFinal) segmentedRoute = false
    }
}
