package gobby.pathfinder

import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.prediction.JumpTracker
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3

internal class PathFallRecovery(private val recovery: PathRecoveryMonitor) {
    var pending = false
        private set

    fun reset() {
        pending = false
    }

    fun handle(waypoints: List<Vec3>, cursor: Int, pos: Vec3, player: LocalPlayer, onLanding: () -> Unit): Boolean {
        if (cursor <= 0 || cursor >= waypoints.size) return false
        if (JumpTracker.isPending() && !pending) return false
        if (pending) return waitForLanding(player, onLanding)
        if (!shouldRecover(waypoints, cursor, pos, player)) return false
        pending = true
        recovery.clearForFall()
        InputManager.releaseAll()
        return true
    }

    private fun waitForLanding(player: LocalPlayer, onLanding: () -> Unit): Boolean {
        InputManager.releaseAll()
        if (player.onGround()) {
            pending = false
            onLanding()
        }
        return true
    }

    private fun shouldRecover(waypoints: List<Vec3>, cursor: Int, pos: Vec3, player: LocalPlayer): Boolean {
        if (player.onGround()) return false
        val deviation = PathFollowMath.segmentDeviation(waypoints, cursor, pos) ?: return false
        if (deviation.segmentDy < -0.2) return false
        return deviation.verticalBelow > FALL_OFF_PATH_Y_DROP || deviation.lateral > FALL_OFF_PATH_LATERAL
    }
}
