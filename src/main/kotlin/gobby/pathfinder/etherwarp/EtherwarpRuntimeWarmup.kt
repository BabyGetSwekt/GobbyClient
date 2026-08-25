package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.navigation.CompiledRoomLandingAtlas
import gobby.pathfinder.navigation.AtlasRoomRoutePlanner
import gobby.pathfinder.navigation.DirectRouteCache
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal object EtherwarpRuntimeWarmup {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gobby-etherwarp-warmup").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    @Volatile private var generation = 0L
    private var activeTask: Future<*>? = null

    @Synchronized
    fun preloadAsync(
        snapshot: BlockCache.SnapshotView,
        grid: Array<MapTile>,
        mapRevision: Long,
        onFinished: (Boolean) -> Unit
    ) {
        val request = ++generation
        activeTask?.cancel(true)
        activeTask = worker.submit {
            if (request != generation) {
                onFinished(false)
                return@submit
            }
            var succeeded = false
            try {
                preload(snapshot, grid, mapRevision)
                succeeded = request == generation
            } finally {
                try {
                    onFinished(succeeded)
                } finally {
                    clearTask(request)
                }
            }
        }
    }

    @Synchronized
    fun cancel() {
        generation++
        activeTask?.cancel(true)
        activeTask = null
    }

    @Synchronized
    private fun clearTask(request: Long) {
        if (request == generation) activeTask = null
    }

    fun preload(snapshot: BlockCache.SnapshotView) {
        EtherwarpPathfinder.preload()
        EtherwarpDirectRoute.preload()
        DirectRouteCache.preload()
        val access = EtherwarpUtils.cachedAccess(snapshot) ?: return
        EtherwarpUtils.etherwarpRaycast(Vec3.ZERO, ZERO_RAY, ZERO_RAY, ZERO_RAY, access)
        warmAimCalculations(access)
        validatePlaceholder(snapshot.trackingMissingChunks())
    }

    fun preload(snapshot: BlockCache.SnapshotView, grid: Array<MapTile>, mapRevision: Long) {
        preload(snapshot)
        if (Thread.currentThread().isInterrupted) return
        DungeonEtherwarpPathfinder.preload(grid, mapRevision)
        if (Thread.currentThread().isInterrupted) return
        val samples = collectAtlasSamples(grid, snapshot)
        if (samples.isEmpty()) return
        val tracked = snapshot.trackingMissingChunks()
        samples.forEach { sample ->
            if (Thread.currentThread().isInterrupted) return
            validateAtlasEdge(sample.edge, tracked)
        }
        samples.firstOrNull()?.let { warmAtlasPlanner(it, grid, tracked) }
    }

    private fun collectAtlasSamples(grid: Array<MapTile>, snapshot: BlockCache.SnapshotView): List<WarmupSample> =
        grid.indices.asSequence()
            .mapNotNull { cell ->
                val room = grid[cell] as? MapTile.Room ?: return@mapNotNull null
                DungeonRooms.component(grid, cell) to room
            }
            .distinctBy { it.first }
            .mapNotNull { (component, room) ->
                if (Thread.currentThread().isInterrupted) return@mapNotNull null
                val edge = CompiledRoomLandingAtlas.candidates(room.data, component, grid, snapshot).edges.firstOrNull()
                edge?.let { WarmupSample(component.first(), room, it) }
            }
            .take(MAX_ATLAS_WARMUP_ROOMS)
            .toList()

    private fun validatePlaceholder(snapshot: BlockCache.SnapshotView) {
        val support = BlockPos(ZERO_COORDINATE, snapshot.minY, ZERO_COORDINATE)
        val origin = node(support, snapshot.minY.toDouble())
        val target = node(support, EtherwarpKind.ETHERWARP.landingY(support.y))
        val range = EtherwarpKind.ETHERWARP.searchRange(EtherwarpPathConfig())
        EtherwarpRouteValidator.validate(listOf(origin, target), range, EtherwarpKind.ETHERWARP, snapshot)
    }

    private fun warmAimCalculations(access: gobby.utils.skyblock.EtherwarpWorldAccess) {
        val config = EtherwarpPathConfig()
        val range = EtherwarpKind.ETHERWARP.searchRange(config)
        val target = BlockPos.ZERO
        val eye = Vec3(ZERO_COORDINATE.toDouble(), AIM_WARMUP_EYE_Y, ZERO_COORDINATE.toDouble())
        EtherwarpUtils.quickAim(target, eye, range, access)
        EtherwarpUtils.aimForBlock(target, eye, range, access)
    }

    private fun node(support: BlockPos, y: Double) = EtherwarpNode(
        ZERO_COORDINATE.toDouble(), y, ZERO_COORDINATE.toDouble(), support,
        ZERO_SCORE, ZERO_SCORE, null, ZERO_ANGLE, ZERO_ANGLE
    )

    private fun validateAtlasEdge(edge: gobby.pathfinder.navigation.PreparedDirectedEdge, snapshot: BlockCache.SnapshotView) {
        val start = EtherwarpNode(
            edge.from.x + CENTER, EtherwarpKind.ETHERWARP.landingY(edge.from.y), edge.from.z + CENTER,
            edge.from, ZERO_SCORE, ZERO_SCORE, null, edge.yaw, edge.pitch
        )
        val target = node(edge.to, EtherwarpKind.ETHERWARP.landingY(edge.to.y))
        EtherwarpRouteValidator.validate(listOf(start, target), EtherwarpKind.ETHERWARP.searchRange(EtherwarpPathConfig()), EtherwarpKind.ETHERWARP, snapshot)
    }

    private fun warmAtlasPlanner(sample: WarmupSample, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView) {
        val from = Vec3(sample.edge.from.x + CENTER, EtherwarpKind.ETHERWARP.landingY(sample.edge.from.y), sample.edge.from.z + CENTER)
        repeat(PLANNER_WARMUP_RUNS) {
            if (Thread.currentThread().isInterrupted) return@repeat
            DungeonSameRoomRoute.find(
                from,
                sample.edge.to,
                EtherwarpKind.ETHERWARP,
                EtherwarpPathConfig(),
                sample.cell,
                sample.cell,
                grid,
                snapshot
            )
            AtlasRoomRoutePlanner.findValidated(
                from,
                sample.edge.to,
                EtherwarpKind.ETHERWARP,
                EtherwarpPathConfig(),
                listOf(RoomStep(sample.room.data, sample.cell, null)),
                grid,
                snapshot,
                SearchDeadline(WARMUP_TIMEOUT_MS)
            )
        }
    }

    private data class WarmupSample(val cell: Int, val room: MapTile.Room, val edge: gobby.pathfinder.navigation.PreparedDirectedEdge)

    private const val ZERO_ANGLE = 0f
    private const val ZERO_RAY = 1.0
    private const val ZERO_COORDINATE = 0
    private const val AIM_WARMUP_EYE_Y = 2.0
    private const val CENTER = 0.5
    private const val ZERO_SCORE = 0.0
    private const val WARMUP_TIMEOUT_MS = 500L
    private const val PLANNER_WARMUP_RUNS = 3
    private const val MAX_ATLAS_WARMUP_ROOMS = 64
}
