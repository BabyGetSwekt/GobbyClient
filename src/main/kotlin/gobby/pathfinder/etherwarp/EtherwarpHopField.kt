package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.rotation.AngleUtils.calcAimAnglesBetween
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import gobby.utils.skyblock.dungeon.map.MapGrid
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.floor

object EtherwarpHopField {
    private const val COLUMN_KEY_SHIFT = 32
    private const val COLUMN_KEY_MASK = 0xFFFFFFFFL
    private const val QUERY_RANGE_MARGIN = 1.0
    private const val INITIAL_DISTANCE = 0
    private const val HOP_INCREMENT = 1
    private const val BLOCK_CENTER_OFFSET = 0.5
    private const val QUERY_BUCKET_SIZE = 4
    private const val ZERO_AIM = 0f
    private const val TOP_FACE_EPSILON = 0.001

    @Volatile
    private var current: Handle? = null

    @Synchronized
    fun request(goal: BlockPos, range: Double, snapshot: BlockCache.SnapshotView, allowedCells: Set<Int>?): Handle {
        val active = current
        if (active?.matches(goal, range, allowedCells) == true && active.isValid()) return active
        val handle = Handle(goal, range, snapshot, allowedCells)
        current = handle
        thread(name = "gobby-etherwarp-field", isDaemon = true) { build(handle) }
        return handle
    }

    fun refresh(handle: Handle): Handle {
        if (handle.isValid()) return handle
        return request(handle.goal, handle.range, BlockCache.freeze(), handle.allowedCells)
    }

    fun forGoal(goal: BlockPos, range: Double): Handle? = current?.takeIf { it.goal == goal && it.range == range && it.isValid() }

    internal fun buildForTesting(
        goal: BlockPos,
        range: Double,
        access: EtherwarpWorldAccess,
        candidates: List<BlockPos>
    ): BuiltField? = Builder(goal, range, access, candidates, null).build()

    private fun build(handle: Handle) {
        val output = runCatching { Builder(handle).build() }.getOrNull()
        if (output == null) {
            handle.fail()
            return
        }
        val dependencies = handle.snapshot.dependencyKeys()
        val published = BlockCache.publishIfCurrent(handle.snapshot, dependencies) {
            handle.publish(output)
        }
        if (!published) handle.fail()
    }

    class Handle internal constructor(
        val goal: BlockPos,
        internal val range: Double,
        internal val snapshot: BlockCache.SnapshotView,
        internal val allowedCells: Set<Int>?
    ) {
        private val worldAccess = EtherwarpWorldAccess(snapshot.minY, snapshot.maxY, snapshot::stateAt)

        @Volatile
        private var field: BuiltField? = null

        @Volatile
        private var dependencies: Set<Long> = emptySet()

        @Volatile
        private var failed = false

        internal fun publish(value: BuiltField) {
            field = value
            dependencies = snapshot.dependencyKeys()
            failed = false
        }

        internal fun fail() {
            failed = true
        }

        fun matches(expectedGoal: BlockPos, expectedRange: Double, expectedCells: Set<Int>?): Boolean =
            goal == expectedGoal && range == expectedRange && allowedCells == expectedCells

        fun isValid(): Boolean = !failed && BlockCache.dependenciesUnchanged(snapshot, dependencies)

        fun query(from: Vec3, currentRange: Double = range): List<EtherwarpNode>? =
            if (isValid()) field?.query(from, currentRange) else null

        internal fun access(): EtherwarpWorldAccess = worldAccess
    }

    private class Builder(
        private val goal: BlockPos,
        private val range: Double,
        private val access: EtherwarpWorldAccess,
        rawCandidates: List<BlockPos>,
        private val allowedCells: Set<Int>?
    ) {
        constructor(handle: Handle) : this(
            handle.goal,
            handle.range,
            handle.access(),
            candidatesFor(handle),
            handle.allowedCells
        )

        private val candidates = rawCandidates.asSequence()
            .filter(::allowed)
            .filter { EtherwarpUtils.isEtherwarpable(it, access) }
            .toList()
        private val candidateKeys = LongOpenHashSet(candidates.size).apply { candidates.forEach { add(it.asLong()) } }
        private val buckets = candidates.groupBy { queryColumnKey(it.x, it.z) }
        private val nodes = HashMap<Long, FieldNode>()
        private val queue = ArrayDeque<BlockPos>()

        fun build(): BuiltField? {
            if (goal !in candidates) return null
            nodes[goal.asLong()] = FieldNode(goal, INITIAL_DISTANCE, null, Aim(ZERO_AIM, ZERO_AIM))
            queue.addLast(goal)
            while (queue.isNotEmpty()) expand(queue.removeFirst())
            return BuiltField(nodes.values.toList(), access)
        }

        private fun expand(target: BlockPos) {
            val targetNode = nodes[target.asLong()] ?: return
            val distance = targetNode.distance + HOP_INCREMENT
            sourcesNear(target).forEach { source ->
                val key = source.asLong()
                if (key in nodes) return@forEach
                if (EtherwarpUtils.quickAim(target, landingEye(source), range, access) == null) return@forEach
                nodes[key] = FieldNode(source, distance, target, forwardAim(source, target))
                queue.addLast(source)
            }
        }

        private fun sourcesNear(target: BlockPos): Sequence<BlockPos> {
            val radius = ceil(range + QUERY_RANGE_MARGIN).toInt()
            val minBucketX = floor((target.x - radius).toDouble() / QUERY_BUCKET_SIZE).toInt()
            val maxBucketX = floor((target.x + radius).toDouble() / QUERY_BUCKET_SIZE).toInt()
            val minBucketZ = floor((target.z - radius).toDouble() / QUERY_BUCKET_SIZE).toInt()
            val maxBucketZ = floor((target.z + radius).toDouble() / QUERY_BUCKET_SIZE).toInt()
            return (minBucketX..maxBucketX).asSequence().flatMap { x ->
                (minBucketZ..maxBucketZ).asSequence().flatMap { z ->
                    buckets[columnKey(x, z)].orEmpty().asSequence()
                        .filter { VecUtils.centerDistanceSq(landingEye(it), target) <= range * range }
                }
            }
        }

        private fun forwardAim(source: BlockPos, target: BlockPos): Aim =
            calcAimAnglesBetween(landingEye(source), topFacePoint(target)).toAim()

        private fun allowed(pos: BlockPos): Boolean = allowedCells?.let { cells ->
            MapGrid.cellOf(pos.x + BLOCK_CENTER_OFFSET, pos.z + BLOCK_CENTER_OFFSET) in cells
        } ?: true
    }

    internal data class FieldNode(val block: BlockPos, val distance: Int, val next: BlockPos?, val aim: Aim)

    internal class BuiltField(nodes: List<FieldNode>, private val access: EtherwarpWorldAccess) {
        private val nodesByBlock = nodes.associateBy { it.block.asLong() }
        private val buckets = nodes.groupBy { queryColumnKey(it.block.x, it.block.z) }
        val nodeCount: Int = nodes.size
        val edgeCount: Int = nodes.count { it.next != null }

        fun query(from: Vec3, range: Double): List<EtherwarpNode>? {
            val candidates = visibleCandidates(from, range)
            val first = candidates.firstNotNullOfOrNull { candidate ->
                EtherwarpUtils.quickAim(candidate.block, eye(from), range, access)?.let { candidate to Aim(it.first, it.second) }
            } ?: return null
            return path(from, first.first.block, first.second)
        }

        private fun visibleCandidates(from: Vec3, range: Double): Sequence<FieldNode> {
            val radius = ceil(range + QUERY_RANGE_MARGIN).toInt()
            val minBucketX = floor((floor(from.x) - radius) / QUERY_BUCKET_SIZE).toInt()
            val maxBucketX = floor((floor(from.x) + radius) / QUERY_BUCKET_SIZE).toInt()
            val minBucketZ = floor((floor(from.z) - radius) / QUERY_BUCKET_SIZE).toInt()
            val maxBucketZ = floor((floor(from.z) + radius) / QUERY_BUCKET_SIZE).toInt()
            return (minBucketX..maxBucketX).asSequence().flatMap { x ->
                (minBucketZ..maxBucketZ).asSequence().flatMap { z ->
                    buckets[columnKey(x, z)].orEmpty().asSequence()
                        .filter { node -> VecUtils.centerDistanceSq(eye(from), node.block) <= range * range }
                }
            }.sortedWith(compareBy({ it.distance }, { VecUtils.centerDistanceSq(eye(from), it.block) }))
        }

        private fun path(from: Vec3, first: BlockPos, firstAim: Aim): List<EtherwarpNode>? {
            val result = mutableListOf(startNode(from, firstAim))
            var current = first
            while (true) {
                val node = nodesByBlock[current.asLong()] ?: return null
                result.add(landingNode(current, node))
                current = node.next ?: return result
            }
        }

        private fun startNode(from: Vec3, aim: Aim): EtherwarpNode =
            EtherwarpNode(from.x, from.y, from.z, BlockPos.containing(from), INITIAL_DISTANCE.toDouble(), INITIAL_DISTANCE.toDouble(), null, aim.yaw, aim.pitch)

        private fun landingNode(pos: BlockPos, node: FieldNode): EtherwarpNode =
            EtherwarpNode(pos.x + BLOCK_CENTER_OFFSET, EtherwarpKind.ETHERWARP.landingY(pos.y), pos.z + BLOCK_CENTER_OFFSET, pos, node.distance.toDouble(), INITIAL_DISTANCE.toDouble(), null, node.aim.yaw, node.aim.pitch)
    }

    private fun topFacePoint(target: BlockPos): Vec3 =
        Vec3(target.x + BLOCK_CENTER_OFFSET, target.y + 1.0 - TOP_FACE_EPSILON, target.z + BLOCK_CENTER_OFFSET)

    private fun landingEye(pos: BlockPos): Vec3 =
        Vec3(pos.x + BLOCK_CENTER_OFFSET, EtherwarpKind.ETHERWARP.landingY(pos.y) + EtherwarpKind.ETHERWARP.eyeHeight(), pos.z + BLOCK_CENTER_OFFSET)

    private fun eye(pos: Vec3): Vec3 = Vec3(pos.x, pos.y + EtherwarpKind.ETHERWARP.eyeHeight(), pos.z)

    private fun columnKey(x: Int, z: Int): Long = (x.toLong() shl COLUMN_KEY_SHIFT) xor (z.toLong() and COLUMN_KEY_MASK)

    private fun queryColumnKey(x: Int, z: Int): Long = columnKey(
        floor(x.toDouble() / QUERY_BUCKET_SIZE).toInt(),
        floor(z.toDouble() / QUERY_BUCKET_SIZE).toInt()
    )

    private fun Pair<Float, Float>.toAim(): Aim = Aim(first, second)
}

private fun candidatesFor(handle: EtherwarpHopField.Handle): List<BlockPos> = handle.snapshot.knownChunkKeys()
    .asSequence()
    .flatMap { key ->
        val candidates = handle.snapshot.peekNonAirCandidates(key)
        if (candidates.any { allowedFor(it, handle.allowedCells) }) {
            handle.snapshot.nonAirCandidates(key).asSequence()
        } else {
            emptySequence()
        }
    }
    .toList()

private fun allowedFor(pos: BlockPos, allowedCells: Set<Int>?): Boolean = allowedCells?.let { cells ->
    MapGrid.cellOf(pos.x + 0.5, pos.z + 0.5) in cells
} ?: true
