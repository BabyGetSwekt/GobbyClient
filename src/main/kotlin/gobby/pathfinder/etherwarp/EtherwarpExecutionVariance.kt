package gobby.pathfinder.etherwarp

import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal class EtherwarpExecutionVariance {
    var enabled = false
        private set
    private var offsets = emptyList<EtherwarpRotationVariance.Offset>()

    fun start(kind: EtherwarpKind, edgeCount: Int, configured: Boolean) {
        enabled = configured && kind == EtherwarpKind.ETHERWARP
        offsets = if (enabled) EtherwarpRotationVariance.generate(edgeCount) else emptyList()
    }

    fun clear() {
        enabled = false
        offsets = emptyList()
    }

    fun resolve(base: Aim, edgeIndex: Int, eye: Vec3, target: BlockPos, range: Double, access: EtherwarpWorldAccess): Aim =
        EtherwarpRotationVariance.resolve(base, offsets.getOrElse(edgeIndex) { EtherwarpRotationVariance.Offset.ZERO }, enabled) { candidate ->
            EtherwarpUtils.validateAim(eye, target, range, candidate.yaw to candidate.pitch, access) != EtherwarpUtils.EtherPos.NONE
        }
}
