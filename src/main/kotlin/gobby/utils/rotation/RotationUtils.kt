package gobby.utils.rotation

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.rotation.AngleUtils.calcAimAngles
import gobby.utils.timer.Clock
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

object RotationUtils {

    val isEasing: Boolean get() = easing
    private var easing = false
    private var onComplete: (() -> Unit)? = null
    private var startYaw = 0f
    private var startPitch = 0f
    private var targetYaw = 0f
    private var targetPitch = 0f
    private val easeClock = Clock()
    private var duration = 0L

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
        val center = Vec3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        val (yaw, pitch) = calcAimAngles(center) ?: return
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
    fun onTick(event: ClientTickEvent.Pre) {
        if (!easing) return
        val player = mc.player ?: return

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
}
