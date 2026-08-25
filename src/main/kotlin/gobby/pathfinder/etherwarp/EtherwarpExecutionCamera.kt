package gobby.pathfinder.etherwarp

import gobby.Gobbyclient.Companion.mc
import gobby.utils.rotation.RotationUtils
import gobby.utils.rotation.ServerRotationLeaseManager

internal class EtherwarpExecutionCamera {
    private var lastServerAim: Aim? = null

    fun remember(mode: EtherwarpExecutionMode, aim: Aim) {
        if (mode != EtherwarpExecutionMode.SERVER_ROTATE) return
        val submitted = ServerRotationLeaseManager.activeRotation()
        lastServerAim = submitted?.let { Aim(it.yaw, it.pitch) } ?: aim
    }

    fun applyFinal(mode: EtherwarpExecutionMode, enabled: Boolean) {
        if (mode != EtherwarpExecutionMode.SERVER_ROTATE || !enabled) return
        val player = mc.player ?: return
        completionAim(mode, enabled, EtherwarpExecutionTermination.ARRIVED, lastServerAim, player.yRot)
            ?.let { RotationUtils.snapTo(it.yaw, it.pitch) }
    }

    internal fun completionAim(
        mode: EtherwarpExecutionMode,
        enabled: Boolean,
        termination: EtherwarpExecutionTermination,
        submitted: Aim? = lastServerAim,
        currentYaw: Float
    ): Aim? = submitted?.takeIf {
        mode == EtherwarpExecutionMode.SERVER_ROTATE && enabled && termination == EtherwarpExecutionTermination.ARRIVED
    }?.let { Aim(RotationUtils.nearestEquivalentYaw(it.yaw, currentYaw), it.pitch) }

    fun clear() {
        lastServerAim = null
    }
}
