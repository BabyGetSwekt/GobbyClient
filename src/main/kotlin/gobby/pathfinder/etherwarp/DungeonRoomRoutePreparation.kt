package gobby.pathfinder.etherwarp

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.DungeonMap
import gobby.pathfinder.navigation.DungeonRoomGoalResolver
import gobby.pathfinder.navigation.DungeonRoomEntryPolicy
import gobby.pathfinder.world.BlockCache
import gobby.utils.findHotbarSlot
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.map.MapConstants
import gobby.utils.skyblock.dungeon.map.MapTile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

internal object DungeonRoomRoutePreparation {
    private val generation = AtomicLong()
    private val preparedCells = ConcurrentHashMap.newKeySet<Int>()
    private val failedCells = ConcurrentHashMap<Int, Long>()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, THREAD_NAME).apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    @Volatile private var preparedRevision = Long.MIN_VALUE

    @Volatile private var active: Future<*>? = null

    @Synchronized
    fun prepare(cells: Set<Int>, priorityCell: Int? = null) {
        val player = mc.player ?: return
        if (findHotbarSlot("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END") < 0) return
        val revision = DungeonMap.revision
        val grid = DungeonMap.grid.copyOf()
        BlockCache.flushDirtyLoadedChunks()
        val snapshot = BlockCache.freeze().trackingMissingChunks()
        resetState(revision)
        if (active?.isDone == false) return
        val prioritized = priorityCell?.takeIf(cells::contains)?.let(::sequenceOf) ?: emptySequence()
        val candidates = (prioritized + cells.asSequence())
            .filter { grid.getOrNull(it) is MapTile.Room }
            .filter(::eligibleForPreparation)
            .filter(preparedCells::add)
            .take(MAX_PREPARED_ROOMS)
            .toList()
        if (candidates.isEmpty()) return
        val from = player.position()
        val referenceY = player.blockPosition().y
        val config = preparationConfig()
        val requestGeneration = generation.incrementAndGet()
        active = worker.submit { prepareCandidates(requestGeneration, candidates, from, referenceY, grid, snapshot, config, revision) }
    }

    @Synchronized
    fun cancel() {
        generation.incrementAndGet()
        active?.cancel(true)
        active = null
        preparedCells.clear()
        failedCells.clear()
        preparedRevision = Long.MIN_VALUE
    }

    private fun prepareCandidates(
        requestGeneration: Long,
        cells: List<Int>,
        from: net.minecraft.world.phys.Vec3,
        referenceY: Int,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        config: EtherwarpPathConfig,
        revision: Long
    ) {
        cells.forEach { cell ->
            if (!isCurrent(requestGeneration, revision)) return
            val goal = DungeonRoomGoalResolver.resolve(cell, referenceY, grid, snapshot, revision) ?: run {
                if (isCurrent(requestGeneration, revision)) {
                    preparedCells.remove(cell)
                    failedCells[cell] = System.nanoTime()
                }
                return@forEach
            }
            val enterRoom = DungeonRoomEntryPolicy.canTeleportInto(grid, cell)
            val route = DungeonEtherwarpPathfinder.findDungeonPath(
                from, goal, EtherwarpKind.ETHERWARP, config, enterRoom, grid,
                { door -> DungeonEtherwarpPathfinder.doorOpen(door) { position -> snapshot.getBlockState(position) } },
                snapshot,
                revision
            )
            if (!isCurrent(requestGeneration, revision)) return
            if (route == null) {
                preparedCells.remove(cell)
                failedCells[cell] = System.nanoTime()
            } else {
                failedCells.remove(cell)
            }
        }
    }

    private fun eligibleForPreparation(cell: Int): Boolean {
        if (cell in preparedCells) return false
        val failedAt = failedCells[cell] ?: return true
        return System.nanoTime() - failedAt >= FAILURE_RETRY_INTERVAL_NANOS
    }

    private fun isCurrent(requestGeneration: Long, revision: Long): Boolean =
        generation.get() == requestGeneration && DungeonMap.revision == revision && !Thread.currentThread().isInterrupted

    private fun resetState(revision: Long) {
        if (!requiresReset(revision, preparedRevision)) return
        active?.cancel(true)
        active = null
        preparedCells.clear()
        failedCells.clear()
        preparedRevision = revision
    }

    internal fun requiresReset(currentRevision: Long, storedRevision: Long): Boolean = currentRevision != storedRevision

    private fun preparationConfig(): EtherwarpPathConfig {
        val range = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: EtherwarpKind.ETHERWARP.defaultRange
        return EtherwarpPathConfig(threads = PREPARATION_THREADS, timeout = PREPARATION_TIMEOUT_MS, etherRange = range)
    }

    private const val PREPARATION_THREADS = 1
    private const val PREPARATION_BUDGET_FACTOR = 8L
    private const val PREPARATION_TIMEOUT_MS = EtherwarpPathConfig.DEFAULT_TIMEOUT_MS * PREPARATION_BUDGET_FACTOR
    private const val FAILURE_RETRY_INTERVAL_NANOS = 1_000_000_000L
    private const val THREAD_NAME = "gobby-route-preparation"
    private val ROOMS_PER_AXIS = (MapConstants.GRID_SIZE + 1) / 2
    private val MAX_PREPARED_ROOMS = ROOMS_PER_AXIS * ROOMS_PER_AXIS
}
