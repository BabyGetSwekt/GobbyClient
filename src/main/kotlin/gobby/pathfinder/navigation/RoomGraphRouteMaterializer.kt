package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal class RoomGraphRouteMaterializer(
    private val start: Vec3,
    private val kind: EtherwarpKind,
    private val positions: List<BlockPos>,
    private val startIndex: Int
) {
    fun materialize(aimByIndex: Map<Int, Aim>, path: List<Int>): List<EtherwarpNode> {
        val indices = withoutRedundantStart(path)
        val nodes = ArrayList<EtherwarpNode>(indices.size)
        indices.forEachIndexed { order, value ->
            val aim = aimByIndex[indices.getOrNull(order + 1)] ?: Aim(ZERO_AIM, ZERO_AIM)
            nodes += if (value == startIndex - 1) startNode(aim) else landingNode(positions[value - startIndex], order, aim)
        }
        nodes.last().yaw = ZERO_AIM
        nodes.last().pitch = ZERO_AIM
        return nodes
    }

    private fun withoutRedundantStart(path: List<Int>): List<Int> =
        if (path.size > 1 && path.first() == startIndex - 1 && positions[path[1] - startIndex] == kind.standingBlock(start)) path.drop(1) else path

    private fun startNode(aim: Aim): EtherwarpNode =
        EtherwarpNode(start.x, start.y, start.z, kind.standingBlock(start), ZERO_SCORE, ZERO_SCORE, null, aim.yaw, aim.pitch)

    private fun landingNode(position: BlockPos, order: Int, aim: Aim): EtherwarpNode = EtherwarpNode(
        position.x + CENTER, kind.landingY(position.y), position.z + CENTER, position,
        order.toDouble(), ZERO_SCORE, null, aim.yaw, aim.pitch
    )

    private companion object {
        const val ZERO_AIM = 0f
        const val ZERO_SCORE = 0.0
        const val CENTER = 0.5
    }
}
