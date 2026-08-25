package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.Executors

object EtherwarpHopField {
    @Volatile
    private var current: Handle? = null
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gobby-etherwarp-field").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    @Synchronized
    fun request(goal: BlockPos, range: Double, snapshot: BlockCache.SnapshotView, allowedCells: Set<Int>?, grid: Array<MapTile>): Handle {
        val active = current
        if (active?.matches(goal, range, allowedCells) == true && (active.isValid() || active.isPending(snapshot))) return active
        active?.cancelBuild()
        val handle = Handle(goal, range, snapshot.trackingMissingChunks(), allowedCells?.toSet(), grid)
        current = handle
        handle.attach(worker.submit { build(handle) })
        return handle
    }

    fun refresh(handle: Handle): Handle {
        if (handle.isValid()) return handle
        return request(handle.goal, handle.range, BlockCache.freeze(), handle.allowedCells, handle.grid)
    }

    fun forGoal(goal: BlockPos, range: Double, allowedCells: Set<Int>? = null): Handle? =
        current?.takeIf { it.matches(goal, range, allowedCells) && it.isValid() }

    fun awaitForGoal(goal: BlockPos, range: Double, allowedCells: Set<Int>?, timeoutNanos: Long): Handle? {
        val active = current?.takeIf { it.matches(goal, range, allowedCells) } ?: return null
        active.awaitBuild(timeoutNanos)
        return active.takeIf { it.isValid() }
    }

    internal fun buildForTesting(
        goal: BlockPos,
        range: Double,
        access: EtherwarpWorldAccess,
        candidates: List<BlockPos>,
        buildBudgetNanos: Long = Long.MAX_VALUE
    ): BuiltField? = EtherwarpHopFieldBuilder(goal, range, access, candidates, null, buildBudgetNanos = buildBudgetNanos).build()

    private fun build(handle: Handle) {
        val started = System.nanoTime()
        val builder = EtherwarpHopFieldBuilder(handle)
        val output = runCatching { builder.build() }.getOrNull()
        if (output == null) {
            handle.fail()
            EtherwarpFieldDiagnostics.logBuild(handle, null, started, builder.candidateCount, builder.candidateNanos, builder)
            return
        }
        val dependencies = handle.snapshot.dependencyKeys()
        val published = BlockCache.publishIfCurrent(handle.snapshot, dependencies) {
            handle.publish(output)
        }
        if (!published) handle.fail()
        EtherwarpFieldDiagnostics.logBuild(handle, output, started, builder.candidateCount, builder.candidateNanos, builder)
    }

    class Handle internal constructor(
        val goal: BlockPos,
        internal val range: Double,
        internal val snapshot: BlockCache.SnapshotView,
        allowedCells: Set<Int>?,
        internal val grid: Array<MapTile>
    ) {
        internal val allowedCells: Set<Int>? = allowedCells?.toSet()
        private val worldAccess = EtherwarpUtils.cachedAccess(snapshot)
            ?: EtherwarpWorldAccess(snapshot.minY, snapshot.maxY, snapshot::stateAt)

        @Volatile
        private var field: BuiltField? = null

        @Volatile
        private var dependencies: Set<Long> = emptySet()

        @Volatile
        private var failed = false

        @Volatile
        private var validatedVersion = Long.MIN_VALUE

        @Volatile
        private var validated = false

        @Volatile
        private var buildTask: java.util.concurrent.Future<*>? = null

        internal fun attach(task: java.util.concurrent.Future<*>) {
            buildTask = task
        }

        internal fun cancelBuild() {
            if (field == null) buildTask?.cancel(true)
        }

        internal fun awaitBuild(timeoutNanos: Long) {
            if (timeoutNanos <= 0L) return
            runCatching { buildTask?.get(timeoutNanos, java.util.concurrent.TimeUnit.NANOSECONDS) }
        }

        internal fun publish(value: BuiltField) {
            field = value
            dependencies = snapshot.dependencyKeys()
            failed = false
            validatedVersion = Long.MIN_VALUE
            validated = false
        }

        internal fun fail() {
            failed = true
        }

        internal fun isPending(currentSnapshot: BlockCache.SnapshotView): Boolean = field == null && !failed && snapshot.worldEpoch == currentSnapshot.worldEpoch && snapshot.cacheVersion == currentSnapshot.cacheVersion

        fun matches(expectedGoal: BlockPos, expectedRange: Double, expectedCells: Set<Int>?): Boolean =
            goal == expectedGoal && range == expectedRange && allowedCells == expectedCells

        fun isValid(): Boolean {
            if (field == null || failed) return false
            val currentVersion = BlockCache.version()
            if (validatedVersion == currentVersion) return validated
            val currentValidity = BlockCache.dependenciesUnchanged(snapshot, dependencies)
            validated = currentValidity
            validatedVersion = currentVersion
            return currentValidity
        }

        fun query(from: Vec3, currentRange: Double = range): List<EtherwarpNode>? =
            if (isValid()) field?.query(from, currentRange) else null

        internal fun access(): EtherwarpWorldAccess = worldAccess
    }
    internal data class FieldNode(val block: BlockPos, val distance: Int, val next: BlockPos?, val aim: Aim)

    internal class BuiltField(nodes: List<FieldNode>, private val access: EtherwarpWorldAccess) {
        private val nodesByBlock = nodes.associateBy { it.block.asLong() }
        private val nodesByHopCount = nodes.sortedBy(FieldNode::distance)
        val nodeCount: Int = nodes.size

        internal fun blocks(): List<BlockPos> = nodesByHopCount.map(FieldNode::block)
        val edgeCount: Int = nodes.count { it.next != null }

        fun query(from: Vec3, range: Double): List<EtherwarpNode>? {
            val candidates = visibleCandidates(from, range)
            val first = candidates.firstNotNullOfOrNull { candidate ->
                EtherwarpUtils.quickAim(candidate.block, EtherwarpFieldGeometry.eye(from), range, access)?.let { candidate to Aim(it.first, it.second) }
            } ?: return null
            return path(from, first.first.block, first.second)
        }

        private fun visibleCandidates(from: Vec3, range: Double): Sequence<FieldNode> {
            val sourceEye = EtherwarpFieldGeometry.eye(from)
            val rangeSq = range * range
            return nodesByHopCount.asSequence()
                .filter { node -> VecUtils.centerDistanceSq(sourceEye, node.block) <= rangeSq }
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
            EtherwarpNode(from.x, from.y, from.z, BlockPos.containing(from), EtherwarpFieldGeometry.INITIAL_DISTANCE.toDouble(), EtherwarpFieldGeometry.INITIAL_DISTANCE.toDouble(), null, aim.yaw, aim.pitch)

        private fun landingNode(pos: BlockPos, node: FieldNode): EtherwarpNode =
            EtherwarpNode(pos.x + EtherwarpFieldGeometry.BLOCK_CENTER_OFFSET, EtherwarpKind.ETHERWARP.landingY(pos.y), pos.z + EtherwarpFieldGeometry.BLOCK_CENTER_OFFSET, pos, node.distance.toDouble(), EtherwarpFieldGeometry.INITIAL_DISTANCE.toDouble(), null, node.aim.yaw, node.aim.pitch)
    }

}
