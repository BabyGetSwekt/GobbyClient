package gobby.gui.map

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.DungeonMap
import gobby.features.dungeons.RoomPathfinder
import gobby.pathfinder.etherwarp.DungeonRoomPathfinder
import gobby.pathfinder.etherwarp.DungeonEtherwarpPathfinder
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.etherwarp.EtherwarpPathExecutor
import gobby.pathfinder.etherwarp.EtherwarpPathfinder
import gobby.pathfinder.world.BlockCache
import gobby.utils.ChatUtils.modMessage
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
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import kotlin.concurrent.thread

class InteractiveMapScreen : Screen(Component.literal("Interactive Map")) {

    private val grid get() = DungeonMap.grid
    private val allDiscovered = BooleanArray(GRID_SIZE * GRID_SIZE) { true }
    private var reachable: Set<Int> = emptySet()
    private val reachabilityClock = Clock()
    private var scale = 1f
    private var originX = 0
    private var originY = 0

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        refreshReachability()
        val mapSize = MapRenderer.getMapSize()
        scale = (minOf(width, height) * MAP_SCALE_FRACTION / mapSize).coerceAtLeast(1f)
        originX = ((width - mapSize * scale) / 2f).toInt()
        originY = ((height - mapSize * scale) / 2f).toInt()
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(context, mouseX, mouseY, delta)
        refreshReachability()
        context.fill(0, 0, width, height, BACKGROUND_GREY)
        drawBorder(context)
        context.pose().pushMatrix()
        context.pose().translate(originX.toFloat(), originY.toFloat())
        context.pose().scale(scale, scale)
        MapRenderer.drawMap(context, grid, DungeonMap.checkmarksView, allDiscovered, DungeonMap.openedDoorsView, false, true, NAME_SCALE_PERCENT, true, true, HEAD_SCALE_PERCENT)
        drawHover(context, mouseX.toDouble(), mouseY.toDouble())
        context.pose().popMatrix()
    }

    private fun refreshReachability() {
        if (!reachabilityClock.hasTimePassed(REACHABILITY_INTERVAL_MS, setTime = true)) return
        DungeonMap.refreshState()
        val playerCell = mc.player?.let { DungeonRooms.roomCellAt(grid, it.x, it.z) }
        reachable = playerCell?.let {
            DungeonRoomPathfinder.reachableCellsFrom(grid, it, opened = { door -> DungeonEtherwarpPathfinder.doorOpen(door) { p -> mc.level?.getBlockState(p)?.isAir == true } })
        }.orEmpty()
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
        if (!isHoldingSkyblockItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")) return modMessage("§cHold an Aspect of the Void or End to transmit")
        val kind = EtherwarpKind.ETHERWARP
        val from = Vec3(player.x, player.y, player.z)
        val mapSnapshot = grid.copyOf()
        val clickedRoom = roomName(cell)
        println("[GobbyMap] click cell=$cell room='$clickedRoom' playerPos=$from kind=$kind")
        val enterRoom = canTeleportInto(cell)
        val config = EtherwarpPathConfig(etherRange = EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: EtherwarpKind.ETHERWARP.defaultRange)
        val bounds = MapGrid.dungeonChunkBounds()
        BlockCache.captureLoadedChunks(bounds[0], bounds[1], bounds[2], bounds[3], refresh = true)
        val goal = roomGoal(cell) ?: return modMessage("Â§cTarget room floor is not loaded yet")
        BlockCache.writeState("click room=$clickedRoom cell=$cell goal=$goal")
        val cacheSnapshot = BlockCache.freeze()
        println("[GobbyMap] requested room='$clickedRoom' goal=$goal enterRoom=$enterRoom")
        thread(name = "gobby-pathfind", isDaemon = true) {
            val clock = Clock()
            val path = DungeonEtherwarpPathfinder.findDungeonPath(from, goal, kind, config, enterRoom, mapSnapshot, { door ->
                DungeonEtherwarpPathfinder.doorOpen(door) { p -> cacheSnapshot.getBlockState(p).isAir }
            }, cacheSnapshot)
            mc.execute {
                val live = path?.let { EtherwarpPathfinder.revalidateLive(it, kind.searchRange(config), kind, cacheSnapshot) }
                if (live == null || live.size < 2) {
                    RoomPathfinder.pathPreview = emptyList()
                    modMessage(if (path == null) "§cNo path to that room" else "§cPath invalid live (target not reachable), retry")
                } else {
                    RoomPathfinder.pathPreview = live
                    val dropped = (path?.size ?: 0) - live.size
                    modMessage("§aPath found: ${live.size - 1} teleports in ${clock.getTime()}ms${if (dropped > 0) " §7(dropped $dropped stale hop${if (dropped > 1) "s" else ""})" else ""}")
                    EtherwarpPathExecutor.start(live, kind, clickedRoom)
                }
            }
        }
    }

    private fun canTeleportInto(cell: Int): Boolean {
        val data = (grid.getOrNull(cell) as? MapTile.Room)?.data ?: return true
        return when (data.type) {
            RoomType.TRAP -> false
            RoomType.PUZZLE -> data.name in ETHERWARPABLE_PUZZLE_ROOMS
            else -> true
        }
    }

    private fun roomName(cell: Int): String =
        (grid.getOrNull(cell) as? MapTile.Room)?.data?.name ?: "cell-$cell"

    private fun roomGoal(cell: Int): BlockPos? {
        val target = scannedCellOf(cell)
        val refY = mc.player?.blockPosition()?.y ?: return null
        val cells = DungeonRooms.component(grid, target)
        val bounds = roomBounds(cells)
        BlockCache.captureLoadedChunks(bounds[0] shr 4, bounds[2] shr 4, bounds[1] shr 4, bounds[3] shr 4, refresh = true)
        val center = BlockPos(MapGrid.worldX(MapGrid.col(target)), refY, MapGrid.worldZ(MapGrid.row(target)))
        val goal = EtherwarpUtils.nearestEtherwarpable(center, cached = true) { DungeonRooms.containingRoomCell(grid, it.x + 0.5, it.z + 0.5) in cells }
        println("[GobbyCache] room goal cell=$cell targetCell=$target center=$center goal=$goal")
        return goal
    }

    private fun roomBounds(cells: Set<Int>): IntArray {
        val x = cells.map { MapGrid.worldX(MapGrid.col(it)) }
        val z = cells.map { MapGrid.worldZ(MapGrid.row(it)) }
        return intArrayOf(x.minOrNull()!! - ROOM_HALF_EXTENT, x.maxOrNull()!! + ROOM_HALF_EXTENT, z.minOrNull()!! - ROOM_HALF_EXTENT, z.maxOrNull()!! + ROOM_HALF_EXTENT)
    }

    private fun scannedCellOf(cell: Int): Int =
        DungeonRooms.component(grid, cell).firstOrNull { (grid.getOrNull(it) as? MapTile.Room)?.core?.let { core -> core != 0 } == true } ?: cell

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
        private val ETHERWARPABLE_PUZZLE_ROOMS = setOf(
            "Creeper Beams", "Higher Blaze", "Ice Fill", "Ice Path",
            "Lower Blaze", "Quiz", "Three Weirdos", "Tic Tac Toe", "Water Board"
        )
        private const val ROOM_HALF_EXTENT = 15
        private const val HOVER_REACHABLE = 0x50FFFFFF
        private const val HOVER_BLOCKED = 0x66FF3030
    }
}
