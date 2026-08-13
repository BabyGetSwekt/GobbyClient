package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.timer.Clock
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object EtherwarpGraphService {

    private const val REBUILD_COOLDOWN_MS = 5_000L
    private const val COARSE_EDGE_RANGE = 28.0
    private const val RESERVED_CORES = 2
    private const val MIN_BUILD_THREADS = 1

    private class Published(val graph: EtherwarpGraph, val cacheVersion: Long, val range: Double, val exact: Boolean)
    private class CachedField(val field: EtherwarpGoalField, val goal: BlockPos, val graph: EtherwarpGraph)

    private val rebuildCooldown = Clock(REBUILD_COOLDOWN_MS)
    private val building = AtomicBoolean(false)
    private val published = AtomicReference<Published?>(null)
    private val cachedField = AtomicReference<CachedField?>(null)
    private val pendingRepair = AtomicReference<BlockPos?>(null)
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gobby-etherwarp-graph").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private val buildPool = ForkJoinPool(
        maxOf(MIN_BUILD_THREADS, Runtime.getRuntime().availableProcessors() - RESERVED_CORES),
        { pool -> ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool).apply { priority = Thread.MIN_PRIORITY } },
        null,
        false
    )

    fun pathTo(from: Vec3, goal: BlockPos, range: Double, accept: (BlockPos) -> Boolean): List<EtherwarpNode>? {
        ensureFresh(range)
        val ready = published.get()?.takeIf { it.range == range } ?: return null
        val access = EtherwarpUtils.cachedAccess(BlockCache.freeze()) ?: return null
        return fieldFor(ready.graph, goal, accept).pathFrom(from, access)
    }

    fun noteDoorChanged(pos: BlockPos) {
        pendingRepair.set(pos)
    }

    fun invalidate() {
        published.set(null)
        cachedField.set(null)
    }

    private fun fieldFor(graph: EtherwarpGraph, goal: BlockPos, accept: (BlockPos) -> Boolean): EtherwarpGoalField {
        cachedField.get()?.takeIf { it.goal == goal && it.graph === graph }?.let { return it.field }
        val built = graph.fieldTo((0 until graph.nodeCount).filter { accept(graph.nodeAt(it)) })
        cachedField.set(CachedField(built, goal, graph))
        return built
    }

    private fun ensureFresh(range: Double) {
        val version = BlockCache.version()
        val ready = published.get()
        val repair = pendingRepair.get()
        if (ready != null && ready.cacheVersion == version && ready.range == range && ready.exact && repair == null) return
        if (!rebuildCooldown.hasTimePassed(setTime = true)) return
        if (!building.compareAndSet(false, true)) return
        val reusable = ready?.takeIf { it.range == range }
        worker.execute { if (reusable != null && repair != null) runRepair(reusable, repair, version) else runBuild(range, version) }
    }

    private fun runRepair(previous: Published, door: BlockPos, version: Long) {
        try {
            val access = EtherwarpUtils.cachedAccess(BlockCache.freeze()) ?: return
            val graph = buildPool.submit<EtherwarpGraph?> {
                EtherwarpGraphBuilder.rebuildAround(previous.graph, door, previous.range, access) { BlockCache.version() != version }
            }.join() ?: return
            published.set(Published(graph, version, previous.range, true))
            cachedField.set(null)
            pendingRepair.compareAndSet(door, null)
        } finally {
            building.set(false)
        }
    }

    private fun runBuild(range: Double, version: Long) {
        try {
            val snapshot = BlockCache.freeze()
            val access = EtherwarpUtils.cachedAccess(snapshot) ?: return
            val candidates = EtherwarpGraphBuilder.candidatesFrom(snapshot, access)
            pendingRepair.set(null)
            val stages = listOf(minOf(COARSE_EDGE_RANGE, range), range).distinct()
            stages.forEachIndexed { stage, edgeRange ->
                val graph = buildPool.submit<EtherwarpGraph?> {
                    EtherwarpGraphBuilder.build(candidates, edgeRange, access) { BlockCache.version() != version }
                }.join() ?: return
                published.set(Published(graph, version, range, stage == stages.lastIndex))
                cachedField.set(null)
            }
        } finally {
            building.set(false)
        }
    }
}
