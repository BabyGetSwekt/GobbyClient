package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.pathfinder.core.PathFinder
import gobby.pathfinder.core.PathNode
import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.utils.render.BlockRenderUtils.draw3DBox
import net.minecraft.util.math.Box
import java.awt.Color
import kotlin.math.*

object PathExecutor {

    private var currentPath: List<PathNode>? = null
    private var currentIndex = 0
    private var isFollowing = false
    var renderPath = true

    private val cTarget = Color(0, 255, 0, 150)
    private val cUpcoming = Color(0, 255, 0, 80)

    private const val LOOKAHEAD = 5
    private const val YAW_SPEED = 0.15f
    private const val NODE_REACH_SQ = 1.5 * 1.5

    fun start(path: List<PathNode>) {
        currentPath = path
        currentIndex = 0
        isFollowing = true
    }

    fun stop() {
        isFollowing = false
        currentPath = null
        currentIndex = 0
        PathFinder.lastPath = null
        InputManager.releaseAll()
    }

    fun isActive(): Boolean = isFollowing

    private fun wrapAngle(a: Float): Float {
        var v = a % 360f
        if (v > 180f) v -= 360f
        if (v < -180f) v += 360f
        return v
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!isFollowing) return
        val path = currentPath ?: return
        val player = mc.player ?: return

        val px = player.x
        val py = player.y
        val pz = player.z

        val scanAhead = min(currentIndex + LOOKAHEAD + 2, path.size)
        for (i in currentIndex until scanAhead) {
            val np = path[i].pos
            val distSq = (px - (np.x + 0.5)) * (px - (np.x + 0.5)) +
                    (py - np.y) * (py - np.y) +
                    (pz - (np.z + 0.5)) * (pz - (np.z + 0.5))
            if (distSq < NODE_REACH_SQ) currentIndex = i + 1
        }
        if (currentIndex >= path.size) {
            stop()
            return
        }

        val lookIdx = min(currentIndex + LOOKAHEAD, path.size - 1)
        val lookTarget = path[lookIdx]
        val dx = (lookTarget.pos.x + 0.5) - player.x
        val dz = (lookTarget.pos.z + 0.5) - player.z
        val targetYaw = (-atan2(dx, dz) * (180.0 / PI)).toFloat()

        val delta = wrapAngle(targetYaw - player.yaw)
        player.yaw += delta * YAW_SPEED

        val dy = path[currentIndex].pos.y - py

        InputManager.releaseAll()
        InputManager.press(MoveAction.FORWARD)
        if (dy > 0) InputManager.press(MoveAction.JUMP)
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!renderPath) return
        val path = if (isFollowing) currentPath else PathFinder.lastPath
        val startIdx = if (isFollowing) currentIndex else 0
        if (path == null) return

        for (i in startIdx until path.size) {
            val box = Box(path[i].pos)
            val color = if (isFollowing && i == currentIndex) cTarget else cUpcoming
            draw3DBox(event.matrixStack, event.camera, box, color, depthTest = false)
        }
    }
}
