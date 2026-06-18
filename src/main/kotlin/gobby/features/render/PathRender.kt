package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.pathfinder.PathExecutor
import gobby.pathfinder.prediction.JumpTracker
import gobby.utils.render.BlockRenderUtils
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.awt.Color

object PathRender {

    private val UPCOMING_COLOR = Color(80, 200, 255, 200)
    private val CURRENT_TARGET_COLOR = Color(80, 255, 120, 230)
    private val PREDICTION_COLOR = Color(255, 200, 60, 220)
    private const val NODE_BOX_SIZE = 0.18
    private const val PREDICTION_NODE_SIZE = 0.06

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!PathExecutor.running()) return

        val waypoints = PathExecutor.currentWaypoints()
        if (waypoints.size < 2) return

        val cursor = PathExecutor.currentCursor()

        drawPathLines(event, waypoints, cursor)
        drawPathNodes(event, waypoints, cursor)
        drawPredictionNodes(event)
    }

    private fun drawPredictionNodes(event: NewRender3DEvent) {
        for (tickPos in JumpTracker.renderPositions()) {
            BlockRenderUtils.drawNode(event.matrixStack, event.camera, tickPos, PREDICTION_NODE_SIZE, PREDICTION_COLOR)
        }
    }

    private fun drawPathLines(event: NewRender3DEvent, waypoints: List<Vec3>, cursor: Int) {
        if (cursor < waypoints.size) {
            mc.player?.position()?.let { playerPos ->
                BlockRenderUtils.drawLine3D(event.matrixStack, event.camera, playerPos, waypoints[cursor], UPCOMING_COLOR, depthTest = false)
            }
        }
        for (i in cursor.coerceAtLeast(0) until waypoints.size - 1) {
            BlockRenderUtils.drawLine3D(event.matrixStack, event.camera, waypoints[i], waypoints[i + 1], UPCOMING_COLOR, depthTest = false)
        }
    }

    private fun drawPathNodes(event: NewRender3DEvent, waypoints: List<Vec3>, cursor: Int) {
        val firstIndex = cursor.coerceAtLeast(0)
        for (index in firstIndex until waypoints.size) {
            val waypoint = waypoints[index]
            val color = if (index == cursor) CURRENT_TARGET_COLOR else UPCOMING_COLOR
            val box = AABB(
                waypoint.x - NODE_BOX_SIZE, waypoint.y - NODE_BOX_SIZE, waypoint.z - NODE_BOX_SIZE,
                waypoint.x + NODE_BOX_SIZE, waypoint.y + NODE_BOX_SIZE, waypoint.z + NODE_BOX_SIZE
            )
            BlockRenderUtils.draw3DBox(event.matrixStack, event.camera, box, color)
        }
    }
}
