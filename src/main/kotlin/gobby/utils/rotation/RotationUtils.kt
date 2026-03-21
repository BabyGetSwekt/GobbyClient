package gobby.utils.rotation

import gobby.Gobbyclient.Companion.mc
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.rotation.AngleUtils.calcAimAngles
import gobby.utils.timer.Clock
import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object RotationUtils {

    val isEasing: Boolean get() = easing
    val isAimLocked: Boolean get() = aimLockTarget != null
    private var aimLockTarget: Entity? = null
    private var easing = false
    private var onComplete: (() -> Unit)? = null
    private var startYaw = 0f
    private var startPitch = 0f
    private var targetYaw = 0f
    private var targetPitch = 0f
    private val easeClock = Clock()
    private var duration = 0L

    fun startAimLock(entity: Entity) {
        aimLockTarget = entity
        easing = false
    }

    fun stopAimLock() {
        aimLockTarget = null
    }

    fun snapTo(yaw: Float, pitch: Float, serverSide: Boolean = false) {
        easing = false
        onComplete = null
        val player = mc.player ?: return
        if (serverSide) {
            mc.networkHandler?.sendPacket(PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround, player.horizontalCollision))
        } else {
            player.yaw = yaw
            player.pitch = pitch
        }
    }

    fun easeToBlock(pos: BlockPos, timeMs: Long, onComplete: (() -> Unit)? = null) {
        easeToVec(Vec3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5), timeMs, onComplete)
    }

    fun easeToVec(target: Vec3d, timeMs: Long, onComplete: (() -> Unit)? = null) {
        val (yaw, pitch) = calcAimAngles(target) ?: return
        easeTo(yaw, pitch, timeMs, onComplete)
    }

    fun easeTo(yaw: Float, pitch: Float, timeMs: Long, onComplete: (() -> Unit)? = null) {
        val player = mc.player ?: return
        startYaw = player.yaw
        startPitch = player.pitch
        targetYaw = startYaw + wrapDelta(yaw - startYaw)
        targetPitch = pitch.coerceIn(-90f, 90f)
        easeClock.update()
        duration = timeMs
        this.onComplete = onComplete
        easing = true
    }

    private fun wrapDelta(delta: Float): Float {
        var d = delta % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }

    @SubscribeEvent
    fun onRender(event: NewRender3DEvent) {
        val player = mc.player ?: return

        val lockTarget = aimLockTarget
        if (lockTarget != null) {
            if (!lockTarget.isAlive || lockTarget.isRemoved) {
                aimLockTarget = null
            } else {
                val delta = event.renderTickCounter.getTickProgress(false)
                val tx = lockTarget.lastRenderX + (lockTarget.x - lockTarget.lastRenderX) * delta
                val ty = lockTarget.lastRenderY + (lockTarget.y - lockTarget.lastRenderY) * delta + lockTarget.height * 0.5
                val tz = lockTarget.lastRenderZ + (lockTarget.z - lockTarget.lastRenderZ) * delta
                val (yaw, pitch) = calcAimAngles(Vec3d(tx, ty, tz)) ?: return
                player.yaw += wrapDelta(yaw - player.yaw) * 0.15f
                player.pitch += (pitch - player.pitch).coerceIn(-90f, 90f) * 0.15f
            }
            return
        }

        if (!easing) return

        val elapsed = easeClock.getTime()
        if (elapsed >= duration) {
            player.yaw = targetYaw
            player.pitch = targetPitch
            easing = false
            onComplete?.invoke()
            onComplete = null
            return
        }

        val progress = easeInOutCubic((elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
        player.yaw = startYaw + (targetYaw - startYaw) * progress
        player.pitch = startPitch + (targetPitch - startPitch) * progress
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        stopAimLock()
        easing = false
        onComplete = null
    }
}
