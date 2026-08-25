package gobby.pathfinder.etherwarp

import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.world.phys.Vec3

internal data class EtherwarpReanchorResult(
    val nodes: List<EtherwarpNode>,
    val index: Int,
    val predictedPosition: Vec3
)

internal object EtherwarpExecutionReanchor {
    fun resolve(
        kind: EtherwarpKind,
        reanchorCount: Int,
        maxReanchors: Int,
        from: Vec3,
        field: EtherwarpHopField.Handle?,
        currentNodes: List<EtherwarpNode>
    ): EtherwarpReanchorResult? {
        if (kind != EtherwarpKind.ETHERWARP || reanchorCount >= maxReanchors) return null
        val range = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: kind.defaultRange
        field?.query(from, range)?.takeIf { it.size >= MIN_ROUTE_SIZE }?.let {
            return EtherwarpReanchorResult(it, FIRST_NODE_INDEX, from)
        }
        val eye = Vec3(from.x, from.y + kind.eyeHeight(), from.z)
        val access = EtherwarpUtils.liveOrCachedAccess() ?: return null
        val index = (currentNodes.size - 1 downTo FIRST_NODE_INDEX)
            .firstOrNull { EtherwarpUtils.quickAim(currentNodes[it].pos, eye, range, access) != null }
            ?: return null
        return EtherwarpReanchorResult(currentNodes, index, from)
    }

    private const val FIRST_NODE_INDEX = 1
    private const val MIN_ROUTE_SIZE = 2
}
