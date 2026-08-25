package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.utils.VecUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal class RoomGraphPositions(
    private val positions: List<BlockPos>,
    private val indexByPosition: Map<BlockPos, Int>,
    private val portals: List<PreparedPortal>,
    private val kind: EtherwarpKind,
    range: Double,
    private val startIndex: Int
) {
    private val rangeSq = range * range

    fun positionOf(index: Int): BlockPos = positions[index - startIndex]

    fun landingEye(index: Int): Vec3 = positionOf(index).let { pos ->
        Vec3(pos.x + CENTER, kind.landingY(pos.y) + kind.eyeHeight(), pos.z + CENTER)
    }

    fun distanceSq(index: Int, reference: BlockPos): Double = VecUtils.centerDistanceSq(landingEye(index), reference)

    fun withinRange(index: Int, reference: BlockPos): Boolean = distanceSq(index, reference) <= rangeSq

    fun withinRange(from: Int, to: Int): Boolean =
        VecUtils.centerDistanceSq(landingEye(from), positionOf(to)) <= rangeSq

    fun nearest(indices: List<Int>, reference: BlockPos, limit: Int): List<Int> =
        indices.asSequence().filter { withinRange(it, reference) }.sortedBy { distanceSq(it, reference) }.take(limit).toList()

    fun nearestPositions(room: PreparedGraphRoom, reference: BlockPos, limit: Int): List<BlockPos> =
        nearest(roomIndices(room), reference, limit).map(::positionOf)

    fun nearestPositions(room: PreparedGraphRoom, reference: Vec3, limit: Int): List<BlockPos> =
        nearestPositions(room, BlockPos.containing(reference), limit)

    fun roomIndices(room: PreparedGraphRoom): List<Int> {
        val portalPositions = portals.asSequence()
            .filter { it.fromCanonical == room.canonical || it.toCanonical == room.canonical }
            .flatMap { it.candidates.asSequence() }
        val ordered = if (room.runtimeBridge) {
            room.liveConnectors + portalPositions.toList() + room.positions + room.anchors + room.runtimeSeeds
        } else {
            room.positions + room.anchors + room.runtimeSeeds + room.liveConnectors + portalPositions.toList()
        }
        return ordered.distinct().mapNotNull(indexByPosition::get)
    }

    private companion object {
        const val CENTER = 0.5
    }
}
