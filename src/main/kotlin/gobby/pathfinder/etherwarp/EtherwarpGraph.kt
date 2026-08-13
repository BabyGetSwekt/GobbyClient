package gobby.pathfinder.etherwarp

import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue

class EtherwarpGraph internal constructor(
    private val nodeKeys: LongArray,
    private val edgeOffset: IntArray,
    private val edgeTarget: IntArray,
    private val buckets: Map<Long, IntArray>,
    val range: Double
) {
    companion object {
        internal const val BUCKET_SIZE = 16
        private const val ENTRY_CANDIDATES = 64
        private const val UNVISITED = -1

        internal fun bucketKey(x: Int, z: Int): Long =
            (Math.floorDiv(x, BUCKET_SIZE).toLong() shl 32) xor (Math.floorDiv(z, BUCKET_SIZE).toLong() and 0xFFFFFFFFL)
    }

    val nodeCount: Int get() = nodeKeys.size
    val edgeCount: Int get() = edgeTarget.size

    fun nodeAt(index: Int): BlockPos = BlockPos.of(nodeKeys[index])

    internal fun neighbours(index: Int): List<Int> = (edgeOffset[index] until edgeOffset[index + 1]).map { edgeTarget[it] }

    internal fun edgeSlice(index: Int): IntArray = edgeTarget.copyOfRange(edgeOffset[index], edgeOffset[index + 1])

    internal fun nodePositions(): List<BlockPos> = nodeKeys.map { BlockPos.of(it) }

    internal fun spatialIndex(): Map<Long, IntArray> = buckets

    fun reachableFrom(from: Vec3, access: EtherwarpWorldAccess): List<Int> {
        val seen = BooleanArray(nodeKeys.size)
        val stack = ArrayDeque<Int>()
        entryNodes(eyeOf(from), access).forEach { if (!seen[it]) { seen[it] = true; stack.addLast(it) } }
        while (stack.isNotEmpty()) {
            val index = stack.removeLast()
            (edgeOffset[index] until edgeOffset[index + 1]).forEach { edge ->
                val next = edgeTarget[edge]
                if (!seen[next]) { seen[next] = true; stack.addLast(next) }
            }
        }
        return seen.indices.filter { seen[it] }
    }

    fun query(from: Vec3, access: EtherwarpWorldAccess, reached: (BlockPos) -> Boolean): List<EtherwarpNode>? {
        val eye = eyeOf(from)
        val entry = entryNodes(eye, access).ifEmpty { return null }
        val cameFrom = IntArray(nodeKeys.size) { UNVISITED }
        val cost = IntArray(nodeKeys.size) { Int.MAX_VALUE }
        val open = PriorityQueue<IntArray>(compareBy { it[1] })
        entry.forEach { index ->
            cost[index] = 1
            open.add(intArrayOf(index, 1))
        }
        while (open.isNotEmpty()) {
            val (index, spent) = open.poll().let { it[0] to it[1] }
            if (spent > cost[index]) continue
            if (reached(nodeAt(index))) return rebuild(from, index, cameFrom)
            relax(index, spent, cost, cameFrom, open)
        }
        return null
    }

    private fun relax(index: Int, spent: Int, cost: IntArray, cameFrom: IntArray, open: PriorityQueue<IntArray>) {
        (edgeOffset[index] until edgeOffset[index + 1]).forEach { edge ->
            val next = edgeTarget[edge]
            if (spent + 1 >= cost[next]) return@forEach
            cost[next] = spent + 1
            cameFrom[next] = index
            open.add(intArrayOf(next, spent + 1))
        }
    }

    private fun entryNodes(eye: Vec3, access: EtherwarpWorldAccess): List<Int> =
        nodesNear(BlockPos.containing(eye))
            .sortedBy { VecUtils.centerDistanceSq(eye, nodeAt(it)) }
            .asSequence()
            .filter { EtherwarpUtils.quickAim(nodeAt(it), eye, range, access) != null }
            .take(ENTRY_CANDIDATES)
            .toList()

    private fun nodesNear(center: BlockPos): List<Int> {
        val reach = (range / BUCKET_SIZE).toInt() + 1
        return (-reach..reach).flatMap { dx ->
            (-reach..reach).flatMap { dz ->
                buckets[bucketKey(center.x + dx * BUCKET_SIZE, center.z + dz * BUCKET_SIZE)]?.asList().orEmpty()
            }
        }
    }

    private fun rebuild(from: Vec3, goalIndex: Int, cameFrom: IntArray): List<EtherwarpNode> =
        toNodes(from, generateSequence(goalIndex) { cameFrom[it].takeIf { previous -> previous != UNVISITED } }.toList().asReversed())

    internal fun toNodes(from: Vec3, chain: List<Int>): List<EtherwarpNode> {
        val start = EtherwarpNode(from.x, from.y, from.z, BlockPos.containing(from), 0.0, 0.0, null, 0f, 0f)
        return listOf(start) + chain.map { index ->
            val block = nodeAt(index)
            EtherwarpNode(block.x + 0.5, EtherwarpKind.ETHERWARP.landingY(block.y), block.z + 0.5, block, 0.0, 0.0, null, 0f, 0f)
        }
    }

    internal fun bestEntry(from: Vec3, access: EtherwarpWorldAccess, usable: (Int) -> Boolean): Int? {
        val eye = eyeOf(from)
        return entryCandidates(BlockPos.containing(eye), eye, usable)
            .firstOrNull { EtherwarpUtils.quickAim(nodeAt(it), eye, range, access) != null }
    }

    private fun entryCandidates(center: BlockPos, eye: Vec3, usable: (Int) -> Boolean): List<Int> {
        val reach = (range / BUCKET_SIZE).toInt() + 1
        val found = ArrayList<Int>(ENTRY_CANDIDATES)
        (0..reach).forEach { ring ->
            if (found.size >= ENTRY_CANDIDATES) return@forEach
            ringOffsets(ring)
                .flatMap { (dx, dz) -> buckets[bucketKey(center.x + dx * BUCKET_SIZE, center.z + dz * BUCKET_SIZE)]?.asList().orEmpty() }
                .filterTo(found, usable)
        }
        return found.sortedBy { VecUtils.centerDistanceSq(eye, nodeAt(it)) }.take(ENTRY_CANDIDATES)
    }

    private fun ringOffsets(ring: Int): List<Pair<Int, Int>> =
        if (ring == 0) listOf(0 to 0)
        else (-ring..ring).flatMap { d -> listOf(d to -ring, d to ring, -ring to d, ring to d) }.distinct()

    fun fieldTo(goals: List<Int>): EtherwarpGoalField {
        val next = IntArray(nodeKeys.size) { EtherwarpGoalField.UNREACHED }
        val distance = IntArray(nodeKeys.size) { EtherwarpGoalField.UNREACHED }
        val queue = ArrayDeque<Int>()
        goals.forEach { if (distance[it] == EtherwarpGoalField.UNREACHED) { distance[it] = 0; queue.addLast(it) } }
        while (queue.isNotEmpty()) {
            val target = queue.removeFirst()
            (reverseOffset[target] until reverseOffset[target + 1]).forEach { edge ->
                val source = reverseTarget[edge]
                if (distance[source] != EtherwarpGoalField.UNREACHED) return@forEach
                distance[source] = distance[target] + 1
                next[source] = target
                queue.addLast(source)
            }
        }
        return EtherwarpGoalField(this, next, distance)
    }

    private val reverseOffset: IntArray
    private val reverseTarget: IntArray

    init {
        val counts = IntArray(nodeKeys.size + 1)
        edgeTarget.forEach { counts[it + 1]++ }
        (1..nodeKeys.size).forEach { counts[it] += counts[it - 1] }
        reverseOffset = counts.copyOf()
        val cursor = counts.copyOf()
        val filled = IntArray(edgeTarget.size)
        nodeKeys.indices.forEach { source ->
            (edgeOffset[source] until edgeOffset[source + 1]).forEach { edge ->
                filled[cursor[edgeTarget[edge]]++] = source
            }
        }
        reverseTarget = filled
    }

    private fun eyeOf(from: Vec3): Vec3 = Vec3(from.x, from.y + EtherwarpKind.ETHERWARP.eyeHeight(), from.z)

    internal fun indexLookup(): Long2IntOpenHashMap {
        val map = Long2IntOpenHashMap(nodeKeys.size)
        nodeKeys.forEachIndexed { index, key -> map.put(key, index) }
        return map
    }
}
