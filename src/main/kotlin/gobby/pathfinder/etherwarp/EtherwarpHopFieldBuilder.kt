package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.PreparedDirectedEdge
import gobby.utils.VecUtils
import gobby.utils.rotation.AngleUtils.calcAimAnglesBetween
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.pathfinder.etherwarp.EtherwarpFieldGeometry.toAim
import net.minecraft.core.BlockPos
import kotlin.math.ceil
import kotlin.math.floor

internal class EtherwarpHopFieldBuilder(
    private val goal: BlockPos,
    private val range: Double,
    private val access: EtherwarpWorldAccess,
    rawCandidates: List<BlockPos>,
    private val allowedCells: Set<Int>?,
    compiledEdges: List<PreparedDirectedEdge> = emptyList(),
    private val buildBudgetNanos: Long = Long.MAX_VALUE,
    grid: Array<MapTile>? = null
) {
    constructor(handle: EtherwarpHopField.Handle) : this(handle, atlasCandidates(handle))
    private constructor(handle: EtherwarpHopField.Handle, atlas: EtherwarpAtlasCandidates) : this(
        handle.goal,
        handle.range,
        handle.access(),
        atlas.positions.ifEmpty { fallbackCandidates(handle) },
        handle.allowedCells,
        atlas.edges,
        if (atlas.positions.isEmpty()) System.nanoTime() + EtherwarpFieldGeometry.FALLBACK_BUILD_BUDGET_NANOS else Long.MAX_VALUE,
        handle.grid
    )
    private val componentByCell: Map<Int, Int> = grid?.let { tiles ->
        allowedCells?.associateWith { cell -> DungeonRooms.component(tiles, cell).minOrNull() ?: cell }
    }.orEmpty()
    private val candidateStart = System.nanoTime()
    private val candidates = collectCandidates(rawCandidates)
    val candidateNanos: Long = System.nanoTime() - candidateStart
    val candidateCount: Int get() = candidates?.size ?: 0
    private val buckets = candidates.orEmpty().groupBy { EtherwarpFieldGeometry.queryColumnKey(it.x, it.z) }
    private val nodes = HashMap<Long, EtherwarpHopField.FieldNode>()
    private val queue = ArrayDeque<BlockPos>()
    private val preparedEdges = compiledEdges.groupBy { it.to }
    var geometricScanned = 0L
        private set
    var geometricRaycasts = 0L
        private set
    var geometricHits = 0L
        private set
    private val compiledConnected = compiledEdges.flatMapTo(HashSet()) { listOf(it.from, it.to) }

    fun build(): EtherwarpHopField.BuiltField? {
        val values = candidates ?: return null
        if (goal !in values) return null
        nodes[goal.asLong()] = EtherwarpHopField.FieldNode(goal, EtherwarpFieldGeometry.INITIAL_DISTANCE, null, Aim(EtherwarpFieldGeometry.ZERO_AIM, EtherwarpFieldGeometry.ZERO_AIM))
        queue.addLast(goal)
        while (queue.isNotEmpty()) {
            if (System.nanoTime() >= buildBudgetNanos || Thread.currentThread().isInterrupted) return null
            expand(queue.removeFirst())
        }
        return EtherwarpHopField.BuiltField(nodes.values.toList(), access)
    }

    private fun collectCandidates(raw: List<BlockPos>): List<BlockPos>? {
        val values = ArrayList<BlockPos>(raw.size)
        for (candidate in raw) {
            if (System.nanoTime() >= buildBudgetNanos || Thread.currentThread().isInterrupted) return null
            if (allowed(candidate) && EtherwarpUtils.isEtherwarpable(candidate, access)) values += candidate
        }
        if (goal !in values && allowed(goal) && EtherwarpUtils.isEtherwarpable(goal, access)) values += goal
        return values
    }

    private fun expand(target: BlockPos) {
        val targetNode = nodes[target.asLong()] ?: return
        val distance = targetNode.distance + EtherwarpFieldGeometry.HOP_INCREMENT
        val prepared = preparedEdges[target].orEmpty()
        prepared.forEach { edge ->
            val key = edge.from.asLong()
            if (key in nodes) return@forEach
            val aim = EtherwarpUtils.validateAim(EtherwarpFieldGeometry.landingEye(edge.from), target, range, edge.yaw to edge.pitch, access)
            if (aim == EtherwarpUtils.EtherPos.NONE) return@forEach
            nodes[key] = EtherwarpHopField.FieldNode(edge.from, distance, target, Aim(edge.yaw, edge.pitch))
            queue.addLast(edge.from)
        }
        val needsGeometricEdge = geometricGate(target)
        sourcesNear(target).forEach { source ->
            geometricScanned++
            val key = source.asLong()
            if (key in nodes) return@forEach
            if (!needsGeometricEdge(source)) return@forEach
            geometricRaycasts++
            if (EtherwarpUtils.quickAim(target, EtherwarpFieldGeometry.landingEye(source), range, access) == null) return@forEach
            geometricHits++
            nodes[key] = EtherwarpHopField.FieldNode(source, distance, target, forwardAim(source, target))
            queue.addLast(source)
        }
    }

    private fun geometricGate(target: BlockPos): (BlockPos) -> Boolean {
        if (target !in compiledConnected) return { true }
        val targetRoom = roomComponentOf(target)
        return { source -> source !in compiledConnected || roomComponentOf(source) != targetRoom }
    }

    private fun roomComponentOf(position: BlockPos): Int? =
        roomCellOf(position)?.let { componentByCell[it] ?: it }

    private fun roomCellOf(position: BlockPos): Int? = MapGrid.roomCellOf(
        position.x + EtherwarpFieldGeometry.BLOCK_CENTER_OFFSET,
        position.z + EtherwarpFieldGeometry.BLOCK_CENTER_OFFSET
    )

    private fun sourcesNear(target: BlockPos): Sequence<BlockPos> {
        val radius = ceil(range + EtherwarpFieldGeometry.QUERY_RANGE_MARGIN).toInt()
        val minBucketX = floor((target.x - radius).toDouble() / EtherwarpFieldGeometry.QUERY_BUCKET_SIZE).toInt()
        val maxBucketX = floor((target.x + radius).toDouble() / EtherwarpFieldGeometry.QUERY_BUCKET_SIZE).toInt()
        val minBucketZ = floor((target.z - radius).toDouble() / EtherwarpFieldGeometry.QUERY_BUCKET_SIZE).toInt()
        val maxBucketZ = floor((target.z + radius).toDouble() / EtherwarpFieldGeometry.QUERY_BUCKET_SIZE).toInt()
        val width = maxBucketX - minBucketX + 1
        val bucketCount = width * (maxBucketZ - minBucketZ + 1)
        val rangeSq = range * range
        return (0 until bucketCount).asSequence().map { offset ->
            val x = minBucketX + offset % width
            val z = minBucketZ + offset / width
            buckets[EtherwarpFieldGeometry.columnKey(x, z)].orEmpty()
        }.flatMap { bucket ->
            bucket.asSequence().filter { VecUtils.centerDistanceSq(EtherwarpFieldGeometry.landingEye(it), target) <= rangeSq }
        }
    }

    private fun forwardAim(source: BlockPos, target: BlockPos): Aim =
        calcAimAnglesBetween(EtherwarpFieldGeometry.landingEye(source), EtherwarpFieldGeometry.topFacePoint(target)).toAim()

    private fun allowed(pos: BlockPos): Boolean = allowedCells?.let { cells ->
        MapGrid.roomCellOf(pos.x + EtherwarpFieldGeometry.BLOCK_CENTER_OFFSET, pos.z + EtherwarpFieldGeometry.BLOCK_CENTER_OFFSET) in cells
    } ?: true
}
