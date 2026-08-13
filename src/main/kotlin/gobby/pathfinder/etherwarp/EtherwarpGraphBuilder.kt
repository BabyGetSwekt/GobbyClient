package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

object EtherwarpGraphBuilder {

    private const val DOOR_REGION_RADIUS = 4
    private const val DOOR_REGION_HEIGHT = 5


    fun candidatesFrom(snapshot: BlockCache.SnapshotView, access: EtherwarpWorldAccess): List<BlockPos> =
        snapshot.knownChunkKeys()
            .flatMap { snapshot.nonAirCandidates(it) }
            .filter { EtherwarpUtils.isEtherwarpable(it, access) }


    fun build(nodes: List<BlockPos>, range: Double, access: EtherwarpWorldAccess, abort: () -> Boolean = { false }): EtherwarpGraph? {
        if (nodes.isEmpty()) return null
        val buckets = bucketize(nodes)
        val perNode = nodes.indices.toList().parallelStream()
            .map { index -> if (abort()) IntArray(0) else neighboursOf(index, nodes, buckets, range, access).toIntArray() }
            .toList()
        val offsets = IntArray(nodes.size + 1)
        perNode.forEachIndexed { index, edges -> offsets[index + 1] = offsets[index] + edges.size }
        val targets = ArrayList<Int>(offsets[nodes.size])
        perNode.forEach { edges -> edges.forEach(targets::add) }
        return EtherwarpGraph(nodes.map { it.asLong() }.toLongArray(), offsets, targets.toIntArray(), buckets, range)
    }

    fun rebuildAround(
        previous: EtherwarpGraph,
        center: BlockPos,
        range: Double,
        access: EtherwarpWorldAccess,
        abort: () -> Boolean = { false }
    ): EtherwarpGraph? {
        val nodes = previous.nodePositions()
        val buckets = previous.spatialIndex()
        val doorCenter = Vec3(center.x + 0.5, center.y + 0.5, center.z + 0.5)
        val radiusSq = range * range
        val nearDoor = BooleanArray(nodes.size) { VecUtils.centerDistanceSq(doorCenter, nodes[it]) <= radiusSq }
        if (!nodeSetUnchanged(nodes, nearDoor, center, access)) return null
        val perNode = nodes.indices.toList().parallelStream()
            .map { index ->
                when {
                    abort() -> IntArray(0)
                    !nearDoor[index] -> previous.edgeSlice(index)
                    else -> unionEdges(previous.edgeSlice(index), addedEdges(index, nodes, buckets, range, access, nearDoor))
                }
            }
            .toList()
        if (abort()) return null
        val offsets = IntArray(nodes.size + 1)
        perNode.forEachIndexed { index, edges -> offsets[index + 1] = offsets[index] + edges.size }
        val targets = ArrayList<Int>(offsets[nodes.size])
        perNode.forEach { edges -> edges.forEach(targets::add) }
        return EtherwarpGraph(nodes.map { it.asLong() }.toLongArray(), offsets, targets.toIntArray(), buckets, range)
    }

    private fun nodeSetUnchanged(
        nodes: List<BlockPos>,
        nearDoor: BooleanArray,
        center: BlockPos,
        access: EtherwarpWorldAccess
    ): Boolean {
        val stillLandable = nodes.indices.none { nearDoor[it] && !EtherwarpUtils.isEtherwarpable(nodes[it], access) }
        if (!stillLandable) return false
        val known = nodes.toHashSet()
        return doorRegion(center).none { EtherwarpUtils.isEtherwarpable(it, access) && it !in known }
    }

    private fun doorRegion(center: BlockPos): Sequence<BlockPos> =
        (-DOOR_REGION_RADIUS..DOOR_REGION_RADIUS).asSequence().flatMap { dx ->
            (-DOOR_REGION_RADIUS..DOOR_REGION_RADIUS).asSequence().flatMap { dz ->
                (-DOOR_REGION_HEIGHT..DOOR_REGION_HEIGHT).asSequence().map { dy -> center.offset(dx, dy, dz) }
            }
        }

    private fun addedEdges(
        index: Int,
        nodes: List<BlockPos>,
        buckets: Map<Long, IntArray>,
        range: Double,
        access: EtherwarpWorldAccess,
        nearDoor: BooleanArray
    ): IntArray {
        val eye = landingEye(nodes[index])
        return nearbyIndices(nodes[index], buckets, range)
            .filter { it != index && nearDoor[it] && VecUtils.centerDistanceSq(eye, nodes[it]) <= range * range }
            .filter { EtherwarpUtils.quickAim(nodes[it], eye, range, access) != null }
            .toIntArray()
    }

    private fun unionEdges(existing: IntArray, added: IntArray): IntArray {
        if (added.isEmpty()) return existing
        val merged = IntOpenHashSet(existing.size + added.size)
        merged.addAll(IntArrayList.wrap(existing))
        merged.addAll(IntArrayList.wrap(added))
        return merged.toIntArray()
    }

    private fun bucketize(nodes: List<BlockPos>): Map<Long, IntArray> =
        nodes.indices.groupBy { EtherwarpGraph.bucketKey(nodes[it].x, nodes[it].z) }
            .mapValues { entry -> entry.value.toIntArray() }

    private fun neighboursOf(
        index: Int,
        nodes: List<BlockPos>,
        buckets: Map<Long, IntArray>,
        range: Double,
        access: EtherwarpWorldAccess
    ): List<Int> {
        val source = nodes[index]
        val eye = landingEye(source)
        return nearbyIndices(source, buckets, range)
            .filter { it != index && VecUtils.centerDistanceSq(eye, nodes[it]) <= range * range }
            .filter { EtherwarpUtils.quickAim(nodes[it], eye, range, access) != null }
    }

    private fun nearbyIndices(center: BlockPos, buckets: Map<Long, IntArray>, range: Double): List<Int> {
        val reach = (range / EtherwarpGraph.BUCKET_SIZE).toInt() + 1
        return (-reach..reach).flatMap { dx ->
            (-reach..reach).flatMap { dz ->
                buckets[EtherwarpGraph.bucketKey(center.x + dx * EtherwarpGraph.BUCKET_SIZE, center.z + dz * EtherwarpGraph.BUCKET_SIZE)]
                    ?.asList().orEmpty()
            }
        }
    }

    private fun landingEye(pos: BlockPos): Vec3 = Vec3(
        pos.x + 0.5,
        EtherwarpKind.ETHERWARP.landingY(pos.y) + EtherwarpKind.ETHERWARP.eyeHeight(),
        pos.z + 0.5
    )
}
