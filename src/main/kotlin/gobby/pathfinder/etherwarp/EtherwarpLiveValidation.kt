package gobby.pathfinder.etherwarp

import gobby.features.dungeons.RoomPathfinder
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.world.phys.Vec3

internal fun revalidateEtherwarpHop(
    valid: MutableList<EtherwarpNode>, path: List<EtherwarpNode>, index: Int,
    kind: EtherwarpKind, range: Double, access: gobby.utils.skyblock.EtherwarpWorldAccess,
    snapshot: BlockCache.SnapshotView?
): Boolean {
    val current = valid.last()
    val eye = Vec3(current.x, current.y + kind.eyeHeight(), current.z)
    val target = path[index + 1].pos
    val storedAim = Aim(current.yaw, current.pitch)
    val aim = EtherwarpUtils.validateAim(eye, target, range, storedAim.yaw to storedAim.pitch, access)
        .takeIf { it != EtherwarpUtils.EtherPos.NONE }?.let { storedAim }
        ?: EtherwarpUtils.aimForBlock(target, eye, range, access)?.let { Aim(it.first, it.second) }
    if (aim == null) {
        if (RoomPathfinder.pathDebug) {
            println("[GobbyPath] revalidation failed hop=${index + 1}${EtherwarpUtils.hopDiagnostic(eye, target, storedAim.yaw to storedAim.pitch, range, snapshot)}")
            println("[GobbyPath]   offsets ${EtherwarpUtils.explainAimFailure(eye, target, range, snapshot)}")
        }
        return false
    }
    current.yaw = aim.yaw
    current.pitch = aim.pitch
    valid.add(path[index + 1])
    return true
}
