package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.pathfinder.core.PathFinder
import gobby.pathfinder.core.PathNode
import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.utils.ChatUtils.modMessage
import gobby.utils.render.BlockRenderUtils.draw3DBox
import net.minecraft.util.math.BlockPos
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

    private const val BASE_SPEED = 0.1f
    private const val BASE_MAX_LOOKAHEAD = 6
    private const val SMOOTH_FACTOR = 0.12f
    private const val FAST_FACTOR = 0.4f
    private const val MID_FACTOR = 0.22f
    private const val PITCH_SMOOTH_FACTOR = 0.15f
    private const val BASE_NODE_REACH_H_SQ = 1.44
    private const val NODE_REACH_V = 1.5
    private const val JUMP_LAND_H_SQ = 0.64
    private const val JUMP_LAND_V = 0.55
    private const val DROP_LAND_H_SQ = 0.81
    private const val DROP_LAND_V = 0.5
    private const val DROP_THRESHOLD = 2
    private const val GROUND_TICKS_TO_JUMP = 2
    private const val JUMP_FORWARD_MAX = 1.3
    private const val JUMP_SIDE_MAX = 0.3
    private const val MOVE_ANGLE_THRESHOLD = 30f
    private const val REPATH_TICKS = 80

    private var smoothYaw = Float.NaN
    private var smoothPitch = Float.NaN
    private var lastGroundYaw = Float.NaN
    private var goalPos: BlockPos? = null
    private var stuckTicks = 0
    private var lastIndex = 0
    private var groundTicks = 0

    fun start(path: List<PathNode>) {
        currentPath = path
        currentIndex = 0
        isFollowing = true
        smoothYaw = Float.NaN
        smoothPitch = Float.NaN
        lastGroundYaw = Float.NaN
        goalPos = path.last().pos
        stuckTicks = 0
        lastIndex = 0
        groundTicks = 0
    }

    fun stop() {
        isFollowing = false
        currentPath = null
        currentIndex = 0
        smoothYaw = Float.NaN
        smoothPitch = Float.NaN
        lastGroundYaw = Float.NaN
        goalPos = null
        stuckTicks = 0
        lastIndex = 0
        groundTicks = 0
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

    private fun adaptiveLookahead(path: List<PathNode>, fromIndex: Int, lookahead: Int): Int {
        if (fromIndex >= path.size - 1) return fromIndex
        if (path[fromIndex].action == MoveAction.JUMP) return fromIndex
        if (isDropLanding(path, fromIndex)) return fromIndex

        val maxIdx = min(fromIndex + lookahead, path.size - 1)
        if (maxIdx <= fromIndex + 1) return maxIdx

        val baseDx = path[fromIndex + 1].pos.x - path[fromIndex].pos.x
        val baseDy = path[fromIndex + 1].pos.y - path[fromIndex].pos.y
        val baseDz = path[fromIndex + 1].pos.z - path[fromIndex].pos.z

        for (i in (fromIndex + 1) until maxIdx) {
            if (path[i + 1].action == MoveAction.JUMP) return i
            if (isDropLanding(path, i + 1)) return i
            val dx = path[i + 1].pos.x - path[i].pos.x
            val dy = path[i + 1].pos.y - path[i].pos.y
            val dz = path[i + 1].pos.z - path[i].pos.z
            if (dx != baseDx || dz != baseDz || dy != baseDy) {
                return min(i, fromIndex + 2)
            }
        }
        return maxIdx
    }

    private fun isDropLanding(path: List<PathNode>, index: Int): Boolean {
        return index > 0 && path[index - 1].pos.y - path[index].pos.y > DROP_THRESHOLD
    }

    private fun repath(player: net.minecraft.client.network.ClientPlayerEntity) {
        val goal = goalPos ?: return
        val speed = player.movementSpeed.toDouble()
        modMessage("§eRepathing...")
        val newPath = PathFinder.findPath(player.blockPos, goal, speed)
        if (newPath != null) {
            currentPath = newPath
            currentIndex = 0
            lastIndex = 0
            stuckTicks = 0
            smoothYaw = Float.NaN
            smoothPitch = Float.NaN
            lastGroundYaw = Float.NaN
        } else {
            modMessage("§cRepath failed, stopping.")
            stop()
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!isFollowing) return
        val path = currentPath ?: return
        val player = mc.player ?: return

        val px = player.x
        val py = player.y
        val pz = player.z

        val speedMult = max(1.0f, player.movementSpeed / BASE_SPEED)
        val nodeReachHSq = BASE_NODE_REACH_H_SQ * speedMult * speedMult
        val maxLookahead = (BASE_MAX_LOOKAHEAD * speedMult).toInt()
        val scanWindow = maxLookahead + 4

        val scanAhead = min(currentIndex + scanWindow, path.size)
        for (i in currentIndex until scanAhead) {
            val np = path[i].pos
            val hdx = px - (np.x + 0.5)
            val hdz = pz - (np.z + 0.5)
            val hDistSq = hdx * hdx + hdz * hdz
            val vDist = abs(py - np.y)

            if (path[i].action == MoveAction.JUMP) {
                if (hDistSq < JUMP_LAND_H_SQ && vDist < JUMP_LAND_V && player.isOnGround) {
                    currentIndex = i + 1
                } else {
                    break
                }
            } else if (isDropLanding(path, i)) {
                if (hDistSq < DROP_LAND_H_SQ && vDist < DROP_LAND_V && player.isOnGround) {
                    currentIndex = i + 1
                } else {
                    break
                }
            } else {
                val reach = if (i == path.size - 1) BASE_NODE_REACH_H_SQ else nodeReachHSq
                if (hDistSq < reach && vDist < NODE_REACH_V) currentIndex = i + 1
            }
        }
        if (currentIndex >= path.size) {
            modMessage("§aPath complete!")
            stop()
            return
        }

        if (currentIndex == lastIndex) {
            stuckTicks++
            if (stuckTicks >= REPATH_TICKS) {
                repath(player)
                return
            }
        } else {
            stuckTicks = 0
            lastIndex = currentIndex
        }

        if (player.isOnGround) {
            groundTicks++
            if (!smoothYaw.isNaN()) lastGroundYaw = smoothYaw
        } else {
            groundTicks = 0
        }

        val currentNode = path[currentIndex]
        val needsJump = currentNode.action == MoveAction.JUMP
        val inJumpArc = needsJump && !player.isOnGround
        val isDropSegment = currentIndex > 0 && path[currentIndex - 1].pos.y - currentNode.pos.y > DROP_THRESHOLD
        val isFreefall = !player.isOnGround && !needsJump && isDropSegment

        val lookIdx = adaptiveLookahead(path, currentIndex, maxLookahead)
        val lookTarget = path[lookIdx]
        val dx = (lookTarget.pos.x + 0.5) - px
        val dz = (lookTarget.pos.z + 0.5) - pz
        val lookHDist = sqrt(dx * dx + dz * dz)

        if (smoothYaw.isNaN()) smoothYaw = player.yaw
        var angleDelta = 0f

        val rotScale = min(speedMult, 3.0f)

        if (isFreefall) {
            if (!lastGroundYaw.isNaN()) smoothYaw = lastGroundYaw
        } else if (lookHDist > 0.1) {
            val targetYaw = (-atan2(dx, dz) * (180.0 / PI)).toFloat()
            angleDelta = abs(wrapAngle(targetYaw - smoothYaw))
            val baseFactor = when {
                angleDelta > 90f -> 0.7f
                angleDelta > 45f -> FAST_FACTOR
                angleDelta > 15f -> MID_FACTOR
                else -> SMOOTH_FACTOR
            }
            val factor = min(baseFactor * rotScale, 1.0f)
            smoothYaw += wrapAngle(targetYaw - smoothYaw) * factor
        }
        player.yaw = smoothYaw

        if (smoothPitch.isNaN()) smoothPitch = player.pitch
        smoothPitch += (0f - smoothPitch) * PITCH_SMOOTH_FACTOR
        player.pitch = smoothPitch

        InputManager.releaseAll()

        if (isFreefall) {
            return
        }

        val angleThreshold = MOVE_ANGLE_THRESHOLD + (speedMult - 1f) * 10f

        if (inJumpArc || angleDelta < angleThreshold) {
            InputManager.press(MoveAction.FORWARD)

            if (needsJump && player.isOnGround && groundTicks >= GROUND_TICKS_TO_JUMP) {
                val target = currentNode.pos
                val tdx = abs((target.x + 0.5) - px)
                val tdz = abs((target.z + 0.5) - pz)
                val canJump = if (currentIndex > 0) {
                    val prev = path[currentIndex - 1].pos
                    val moveDx = abs(target.x - prev.x)
                    val moveDz = abs(target.z - prev.z)
                    val flatDist = moveDx * tdx + moveDz * tdz
                    val sideDist = moveDz * tdx + moveDx * tdz
                    flatDist <= JUMP_FORWARD_MAX && sideDist <= JUMP_SIDE_MAX
                } else true
                if (canJump) InputManager.press(MoveAction.JUMP)
            }
        }
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
