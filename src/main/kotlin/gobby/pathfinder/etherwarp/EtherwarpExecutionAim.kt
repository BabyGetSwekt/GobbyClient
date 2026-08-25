package gobby.pathfinder.etherwarp

import gobby.Gobbyclient.Companion.mc
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.world.phys.Vec3

internal object EtherwarpExecutionAim {
    fun resolve(
        nodes: List<EtherwarpNode>,
        index: Int,
        kind: EtherwarpKind,
        predictedPosition: Vec3?,
        variance: EtherwarpExecutionVariance,
        useLiveOrigin: Boolean
    ): Aim? {
        val target = nodes.getOrNull(index) ?: return null
        val source = nodes[index - 1]
        val origin = currentOrigin(predictedPosition, source, useLiveOrigin)
        val eye = Vec3(origin.x, origin.y + kind.eyeHeight(), origin.z)
        val range = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: kind.defaultRange
        val access = EtherwarpUtils.liveOrCachedAccess() ?: return null
        val base = kindBaseAim(kind, source, target, eye, range, access) ?: return null
        return if (kind == EtherwarpKind.ETHERWARP) {
            variance.resolve(base, index - 1, eye, target.pos, range, access)
        } else {
            base
        }
    }

    private fun currentOrigin(predictedPosition: Vec3?, source: EtherwarpNode, useLiveOrigin: Boolean): Vec3 =
        if (useLiveOrigin) mc.player?.let { Vec3(it.x, it.y, it.z) } ?: predictedPosition ?: source.eye
        else predictedPosition ?: source.eye

    private fun kindBaseAim(
        kind: EtherwarpKind,
        source: EtherwarpNode,
        target: EtherwarpNode,
        eye: Vec3,
        range: Double,
        access: gobby.utils.skyblock.EtherwarpWorldAccess
    ): Aim? {
        if (kind != EtherwarpKind.ETHERWARP) return kind.aimAt(eye, target.pos, range, cached = false)
        val stored = Aim(source.yaw, source.pitch)
        val valid = EtherwarpUtils.validateAim(eye, target.pos, range, stored.yaw to stored.pitch, access)
        return if (valid != EtherwarpUtils.EtherPos.NONE) stored else {
            EtherwarpUtils.aimForBlock(target.pos, eye, range, access)?.let { Aim(it.first, it.second) }
        }
    }
}
