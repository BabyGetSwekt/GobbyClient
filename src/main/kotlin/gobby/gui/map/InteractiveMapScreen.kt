package gobby.gui.map

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.DungeonMap
import gobby.features.dungeons.RoomPathfinder
import gobby.pathfinder.etherwarp.DungeonRoomPathfinder
import gobby.pathfinder.etherwarp.DungeonEtherwarpPathfinder
import gobby.pathfinder.etherwarp.SearchDeadline
import gobby.pathfinder.etherwarp.DungeonPathPlanningExecutor
import gobby.pathfinder.etherwarp.DungeonRoomRoutePreparation
import gobby.pathfinder.etherwarp.DungeonPreparedPathLookup
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.etherwarp.EtherwarpPathExecutor
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpPartialRouteObserver
import gobby.pathfinder.navigation.DungeonRoomGoalResolver
import gobby.pathfinder.navigation.DungeonRoomEntryPolicy
import gobby.pathfinder.world.BlockCache
import gobby.utils.ChatUtils.modMessage
import gobby.utils.findEtherwarpableHotbarSlot
import gobby.utils.isHoldingSkyblockItem
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.timer.Clock
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_SIZE
import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_STRIDE
import gobby.utils.skyblock.dungeon.map.MapConstants.GAP
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.map.MapConstants.STEP
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapRenderer
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

class InteractiveMapScreen : Screen(Component.literal("Interactive Map")) {
    private val grid get() = DungeonMap.grid
    private val allDiscovered = BooleanArray(GRID_SIZE * GRID_SIZE) { true }
    private var reachable: Set<Int> = emptySet()
    private val reachabilityClock = Clock()
    private var scale = 1f
    private var originX = 0
    private var originY = 0
    private var routePlanningContinues = false

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        refreshReachability(force = true)
        val mapSize = MapRenderer.getMapSize()
        scale = (minOf(width, height) * MAP_SCALE_FRACTION / mapSize).coerceAtLeast(1f)
        originX = ((width - mapSize * scale) / 2f).toInt()
        originY = ((height - mapSize * scale) / 2f).toInt()
    }

    override fun onClose() {
        DungeonRoomRoutePreparation.cancel()
        if (!routePlanningContinues) {
            DungeonPathPlanningExecutor.cancel()
            EtherwarpPathExecutor.cancel()
        }
        super.onClose()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(context, mouseX, mouseY, delta)
        refreshReachability(priorityCell = cellAt(mouseX.toDouble(), mouseY.toDouble()))
        context.fill(0, 0, width, height, BACKGROUND_GREY)
        drawBorder(context)
        context.pose().pushMatrix()
        context.pose().translate(originX.toFloat(), originY.toFloat())
        context.pose().scale(scale, scale)
        MapRenderer.drawMap(context, grid, DungeonMap.checkmarksView, allDiscovered, DungeonMap.openedDoorsView, false, true, NAME_SCALE_PERCENT, true, true, HEAD_SCALE_PERCENT)
        drawHover(context, mouseX.toDouble(), mouseY.toDouble())
        context.pose().popMatrix()
    }

    private fun refreshReachability(force: Boolean = false, priorityCell: Int? = null) {
        if (!force && !reachabilityClock.hasTimePassed(REACHABILITY_INTERVAL_MS, setTime = true)) return
        DungeonMap.refreshState()
        BlockCache.flushDirtyLoadedChunks()
        val playerCell = mc.player?.let { DungeonRooms.roomCellAt(grid, it.x, it.z) }
        reachable = playerCell?.let {
            DungeonRoomPathfinder.reachableCellsFrom(grid, it, opened = { door -> DungeonEtherwarpPathfinder.doorOpen(door) { p -> BlockCache.knownStateAt(p) } })
        }.orEmpty()
        DungeonRoomRoutePreparation.prepare(reachable, priorityCell)
    }

    private fun drawBorder(context: GuiGraphicsExtractor) {
        val mapSize = MapRenderer.getMapSize()
        val x0 = originX
        val y0 = originY
        val x1 = originX + (mapSize * scale).toInt()
        val y1 = originY + (mapSize * scale).toInt()
        val t = BORDER_THICKNESS
        context.fill(x0 - t, y0 - t, x1 + t, y0, BORDER_GREY)
        context.fill(x0 - t, y1, x1 + t, y1 + t, BORDER_GREY)
        context.fill(x0 - t, y0, x0, y1, BORDER_GREY)
        context.fill(x1, y0, x1 + t, y1, BORDER_GREY)
    }

    private fun drawHover(context: GuiGraphicsExtractor, screenX: Double, screenY: Double) {
        val cell = cellAt(screenX, screenY) ?: return
        if (grid.getOrNull(cell) !is MapTile.Room) return
        val color = if (cell in reachable) HOVER_REACHABLE else HOVER_BLOCKED
        val cells = DungeonRooms.component(grid, cell)
        cells.forEach { fillRoomCell(context, it, cells, color) }
    }

    private fun fillRoomCell(context: GuiGraphicsExtractor, cell: Int, cells: Set<Int>, color: Int) {
        val col = MapGrid.col(cell)
        val row = MapGrid.row(cell)
        val px = MapGrid.screenX(col)
        val py = MapGrid.screenY(row)
        val extendX = if (MapGrid.inRange(col + CELL_STRIDE, row) && MapGrid.index(col + CELL_STRIDE, row) in cells) GAP else 0
        val extendY = if (MapGrid.inRange(col, row + CELL_STRIDE) && MapGrid.index(col, row + CELL_STRIDE) in cells) GAP else 0
        context.fill(px, py, px + CELL_SIZE + extendX, py + CELL_SIZE + extendY, color)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() != LEFT_BUTTON) return super.mouseClicked(click, doubled)
        val cell = cellAt(click.x(), click.y())
        if (cell != null && grid.getOrNull(cell) is MapTile.Room) {
            startPathTo(cell)
            onClose()
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    private fun startPathTo(cell: Int) {
        val player = mc.player ?: return
        if (!hasEtherwarpItem()) return modMessage("Hold an Aspect of the Void or End to transmit")
        cancelPathPlanning()
        beginInteractiveRoutePlanning()
        val kind = EtherwarpKind.ETHERWARP
        val route = prepareRoute(cell, Vec3(player.x, player.y, player.z), kind) ?: run { EtherwarpPathExecutor.endPlanningSneak()
        return }
        if (startPreparedRoute(route)) return
        startAsyncRoute(route)
    }

    private fun hasEtherwarpItem(): Boolean =
        isHoldingSkyblockItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END") || findEtherwarpableHotbarSlot() >= 0

    private fun cancelPathPlanning() {
        DungeonPathPlanningExecutor.cancel()
        EtherwarpPathExecutor.cancel()
    }

    private fun prepareRoute(cell: Int, from: Vec3, kind: EtherwarpKind): PendingRoute? {
        val snapshot = grid.copyOf()
        val enterRoom = DungeonRoomEntryPolicy.canTeleportInto(snapshot, cell)
        val config = EtherwarpPathConfig(etherRange = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: kind.defaultRange)
        val deadline = SearchDeadline(config.timeout)
        val bounds = MapGrid.dungeonChunkBounds()
        BlockCache.captureLoadedChunks(bounds[0], bounds[1], bounds[2], bounds[3], refresh = true)
        val cacheSnapshot = BlockCache.freeze().trackingMissingChunks()
        val goal = roomGoal(cell, cacheSnapshot) ?: run {
            modMessage("Target room floor is not loaded yet")
            return null
        }
        return PendingRoute(from, goal, kind, snapshot, enterRoom, config, deadline, cacheSnapshot)
    }

    private fun startPreparedRoute(route: PendingRoute): Boolean {
        val path = DungeonPreparedPathLookup.take(route.from, route.goal, route.kind, route.config, route.enterRoom, route.snapshot, route.cacheSnapshot)
            ?: return false
        RoomPathfinder.pathPreview = path
        modMessage("Path found: " + (path.size - 1) + " teleports from prepared cache")
        EtherwarpPathExecutor.start(path, route.kind, DungeonEtherwarpPathfinder.hopFieldFor(route.goal, route.kind, route.config), observer = partialRouteObserver(route))
        routePlanningContinues = true
        return true
    }

    private fun startAsyncRoute(route: PendingRoute) {
        DungeonPathPlanningExecutor.submit { requestId ->
            val clock = Clock()
            val result = runCatching {
                DungeonEtherwarpPathfinder.findDungeonPath(route.from, route.goal, route.kind, route.config, route.enterRoom, route.snapshot, { door ->
                    DungeonEtherwarpPathfinder.doorOpen(door) { position -> route.cacheSnapshot.getBlockState(position) }
                }, route.cacheSnapshot, deadline = route.deadline)
            }.onFailure { failure ->
                println("[GobbyClient] route planning to ${route.goal} threw $failure")
                failure.printStackTrace()
            }
            finishAsyncRoute(requestId, result, route, clock, DungeonEtherwarpPathfinder.lastSearchTimedOut)
        }
        routePlanningContinues = true
        beginInteractiveRoutePlanning()
    }

    private fun finishAsyncRoute(requestId: Long, path: Result<List<EtherwarpNode>?>, route: PendingRoute, clock: Clock, timedOut: Boolean) {
        mc.execute {
            if (!DungeonPathPlanningExecutor.isCurrent(requestId)) return@execute
            val resolvedPath = path.getOrNull()
            if (path.isFailure) EtherwarpPathExecutor.endPlanningSneak()
            if (resolvedPath == null || resolvedPath.size < 2) handleFailedRoute(resolvedPath, timedOut, path.exceptionOrNull())
            else {
                RoomPathfinder.pathPreview = resolvedPath
                modMessage("Path found: " + (resolvedPath.size - 1) + " teleports in " + clock.getTime() + "ms")
                EtherwarpPathExecutor.start(resolvedPath, route.kind, DungeonEtherwarpPathfinder.hopFieldFor(route.goal, route.kind, route.config), observer = partialRouteObserver(route))
            }
        }
    }

    private fun handleFailedRoute(path: List<EtherwarpNode>?, timedOut: Boolean, error: Throwable? = null) {
        EtherwarpPathExecutor.endPlanningSneak()
        RoomPathfinder.pathPreview = emptyList()
        when {
            error != null -> modMessage("Route planning failed with ${error::class.simpleName}, see the log")
            timedOut -> modMessage("Search timed out before reaching that room, try again")
            path != null -> modMessage("Path invalid in the frozen world snapshot, retry")
            else -> modMessage("No path to that room")
        }
    }

    private fun partialRouteObserver(route: PendingRoute) = EtherwarpPartialRouteObserver(continueRoute = { restartAfterPartial(route) }); private fun restartAfterPartial(route: PendingRoute) {
        val player = mc.player ?: return
        BlockCache.flushDirtyLoadedChunks()
        val cache = BlockCache.freeze().trackingMissingChunks()
        startAsyncRoute(route.copy(from = Vec3(player.x, player.y, player.z), snapshot = grid.copyOf(), deadline = SearchDeadline(route.config.timeout), cacheSnapshot = cache))
    }

    private fun roomName(cell: Int): String =
        (grid.getOrNull(cell) as? MapTile.Room)?.data?.name ?: "cell-$cell"

    private fun roomGoal(cell: Int, snapshot: BlockCache.SnapshotView): BlockPos? {
        val refY = mc.player?.blockPosition()?.y ?: return null
        return DungeonRoomGoalResolver.resolve(cell, refY, grid, snapshot, DungeonMap.revision)
    }

    private fun cellAt(screenX: Double, screenY: Double): Int? {
        val localX = (screenX - originX) / scale
        val localY = (screenY - originY) / scale
        if (localX < 0.0 || localY < 0.0) return null
        val stepCol = (localX / STEP).toInt()
        val stepRow = (localY / STEP).toInt()
        val col = stepCol * CELL_STRIDE + if (localX - stepCol * STEP >= CELL_SIZE) 1 else 0
        val row = stepRow * CELL_STRIDE + if (localY - stepRow * STEP >= CELL_SIZE) 1 else 0
        if (!MapGrid.inRange(col, row)) return null
        val cell = MapGrid.index(col, row)
        return when (grid.getOrNull(cell)) {
            is MapTile.Room -> cell
            is MapTile.Connection -> connectionRoomCell(col, row)
            else -> null
        }
    }

    private fun connectionRoomCell(col: Int, row: Int): Int? {
        val neighbours = if (col % CELL_STRIDE == 1) listOf(col - 1 to row, col + 1 to row) else listOf(col to row - 1, col to row + 1)
        return neighbours.firstNotNullOfOrNull { (c, r) -> MapGrid.index(c, r).takeIf { MapGrid.inRange(c, r) && grid.getOrNull(it) is MapTile.Room } }
    }
    companion object {
        private const val LEFT_BUTTON = 0
        private const val MAP_SCALE_FRACTION = 0.75f
        private const val NAME_SCALE_PERCENT = 100
        private const val HEAD_SCALE_PERCENT = 100
        private const val REACHABILITY_INTERVAL_MS = 250L
        private const val BACKGROUND_GREY = 0xC0303030.toInt()
        private const val BORDER_GREY = 0xFFB0B0B0.toInt()
        private const val BORDER_THICKNESS = 2
        private const val HOVER_REACHABLE = 0x50FFFFFF
        private const val HOVER_BLOCKED = 0x66FF3030
    }
    private data class PendingRoute(
        val from: Vec3,
        val goal: BlockPos,
        val kind: EtherwarpKind,
        val snapshot: Array<MapTile>,
        val enterRoom: Boolean,
        val config: EtherwarpPathConfig,
        val deadline: SearchDeadline,
        val cacheSnapshot: BlockCache.SnapshotView
    )
}
