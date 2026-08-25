package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.utils.VecUtils
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_STRIDE
import gobby.utils.skyblock.dungeon.tiles.RoomData
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos

object MapScanner {

    private const val CHUNK_SHIFT = 4
    private const val COORDS_PER_CELL = 2

    /**
     * Two-pass scan: rooms first at even grid positions, then gaps resolved by neighbors.
     * Returns true when all chunks are loaded.
     */

    fun scan(grid: Array<MapTile>): Boolean {
        val world = mc.level ?: return false
        var allLoaded = true

        MapGrid.roomCells.forEach { cell ->
            if (grid[cell.index] !is MapTile.Empty) return@forEach
            when (val scanned = scanRoomCell(world, cell)) {
                ScanResult.ChunkMissing -> allLoaded = false
                ScanResult.NoRoom -> Unit
                is ScanResult.Found -> grid[cell.index] = scanned.room
            }
        }

        inferRooms(grid)

        MapGrid.gapCells.forEach { cell ->
            if (grid[cell.index] !is MapTile.Empty) return@forEach
            grid[cell.index] = resolveGap(grid, cell.col, cell.row) ?: return@forEach
        }

        return allLoaded
    }

    private sealed interface ScanResult {
        data object ChunkMissing : ScanResult
        data object NoRoom : ScanResult
        data class Found(val room: MapTile.Room) : ScanResult
    }

    private fun scanRoomCell(world: ClientLevel, cell: GridCell): ScanResult {
        val xPos = MapGrid.worldX(cell.col)
        val zPos = MapGrid.worldZ(cell.row)
        val chunk = world.chunkSource.getChunk(xPos shr CHUNK_SHIFT, zPos shr CHUNK_SHIFT, false)
        if (chunk == null || chunk.isEmpty) return ScanResult.ChunkMissing
        val position = VecUtils.Vec2(xPos, zPos)
        val height = ScanUtils.getTopLayerOfRoom(position, chunk)
        if (height == 0) return ScanResult.NoRoom
        val core = ScanUtils.getCoreAtHeight(position, height, chunk)
        val roomData = ScanUtils.coreToRoomData[core] ?: return ScanResult.NoRoom
        return ScanResult.Found(MapTile.Room(roomData, core))
    }

    /**
     * Infers missing cells for multi-cell rooms by elimination.
     * For each scanned room cell, generates all possible arrangements for its shape,
     * eliminates arrangements where any position has a different room,
     * and fills in empty cells if only 1 valid arrangement remains.
     * Loops until no more changes (cascading inference).
     */

    private fun inferRooms(grid: Array<MapTile>) {
        while (inferencePass(grid)) Unit
    }

    private fun inferencePass(grid: Array<MapTile>): Boolean =
        MapGrid.roomCells.fold(false) { changed, cell -> inferCell(grid, cell) || changed }

    private fun inferCell(grid: Array<MapTile>, cell: GridCell): Boolean {
        val room = grid[cell.index] as? MapTile.Room ?: return false
        val arrangements = getArrangements(room.data.shape, cell.col, cell.row) ?: return false
        val block = arrangements.singleOrNull { candidate ->
            isValidBlock(grid, room.data, candidate) && !wouldBlockOtherRoom(grid, room.data, candidate) && !crossesDoor(candidate)
        } ?: return false
        return fillEmptyCells(grid, room.data, block).isNotEmpty()
    }

    private fun blockIndices(block: IntArray): List<Int> =
        (block.indices step COORDS_PER_CELL).map { MapGrid.index(block[it], block[it + 1]) }

    private fun fillEmptyCells(grid: Array<MapTile>, data: RoomData, block: IntArray): List<Int> =
        blockIndices(block).filter { grid[it] is MapTile.Empty }
            .onEach { grid[it] = MapTile.Room(data, MapConstants.UNKNOWN_CORE) }

    /**
     * Lookahead: tentatively fills a block, then checks if any other scanned
     * multi-cell room would have 0 valid arrangements left. If so, this
     * arrangement is invalid — it would block another room.
     */

    private fun wouldBlockOtherRoom(grid: Array<MapTile>, data: RoomData, block: IntArray): Boolean {
        val tentative = fillEmptyCells(grid, data, block)
        val blocked = MapGrid.roomCells.any { cell -> leavesNoArrangement(grid, data, cell) }
        tentative.forEach { grid[it] = MapTile.Empty }
        return blocked
    }

    private fun leavesNoArrangement(grid: Array<MapTile>, data: RoomData, cell: GridCell): Boolean {
        val other = grid[cell.index] as? MapTile.Room ?: return false
        if (other.data === data) return false
        val arrangements = getArrangements(other.data.shape, cell.col, cell.row) ?: return false
        return arrangements.none { isValidBlock(grid, other.data, it) }
    }

    /** Generates all possible arrangements for a room shape at a given cell position. */

    private fun getArrangements(shape: String, col: Int, row: Int): List<IntArray>? {
        return when (shape) {
            "1x2" -> buildLinearArrangements(2, col, row)
            "1x3" -> buildLinearArrangements(3, col, row)
            "1x4" -> buildLinearArrangements(4, col, row)
            "2x2" -> listOf(
                intArrayOf(col, row, col + 2, row, col, row + 2, col + 2, row + 2),
                intArrayOf(col - 2, row, col, row, col - 2, row + 2, col, row + 2),
                intArrayOf(col, row - 2, col + 2, row - 2, col, row, col + 2, row),
                intArrayOf(col - 2, row - 2, col, row - 2, col - 2, row, col, row),
            )
            "L" -> buildLArrangements(col, row)
            else -> null // 1x1: no inference
        }
    }

    /** Builds all horizontal + vertical arrangements for a 1xN linear room. */

    private fun buildLinearArrangements(size: Int, col: Int, row: Int): List<IntArray> =
        (0 until size).map { offset -> horizontalRun(size, col - offset * CELL_STRIDE, row) } +
            (0 until size).map { offset -> verticalRun(size, col, row - offset * CELL_STRIDE) }

    private fun horizontalRun(size: Int, startCol: Int, row: Int): IntArray =
        IntArray(size * COORDS_PER_CELL) { slot -> if (isColumnSlot(slot)) startCol + cellOfSlot(slot) * CELL_STRIDE else row }

    private fun verticalRun(size: Int, col: Int, startRow: Int): IntArray =
        IntArray(size * COORDS_PER_CELL) { slot -> if (isColumnSlot(slot)) col else startRow + cellOfSlot(slot) * CELL_STRIDE }

    private fun isColumnSlot(slot: Int): Boolean = slot % COORDS_PER_CELL == 0

    private fun cellOfSlot(slot: Int): Int = slot / COORDS_PER_CELL

    /** Builds all 12 possible L-shape arrangements for a cell at (col, row). */

    private fun buildLArrangements(col: Int, row: Int): List<IntArray> {
        val list = mutableListOf<IntArray>()
        list.add(intArrayOf(col, row, col + 2, row, col, row + 2))       // right + down
        list.add(intArrayOf(col, row, col + 2, row, col, row - 2))       // right + up
        list.add(intArrayOf(col, row, col - 2, row, col, row + 2))       // left + down
        list.add(intArrayOf(col, row, col - 2, row, col, row - 2))       // left + up

        list.add(intArrayOf(col - 2, row, col, row, col - 2, row + 2))   // corner left, arm down
        list.add(intArrayOf(col - 2, row, col, row, col - 2, row - 2))   // corner left, arm up
        list.add(intArrayOf(col + 2, row, col, row, col + 2, row + 2))   // corner right, arm down
        list.add(intArrayOf(col + 2, row, col, row, col + 2, row - 2))   // corner right, arm up

        list.add(intArrayOf(col, row - 2, col + 2, row - 2, col, row))   // corner above, arm right
        list.add(intArrayOf(col, row - 2, col - 2, row - 2, col, row))   // corner above, arm left
        list.add(intArrayOf(col, row + 2, col + 2, row + 2, col, row))   // corner below, arm right
        list.add(intArrayOf(col, row + 2, col - 2, row + 2, col, row))   // corner below, arm left
        return list
    }

    private fun isValidBlock(grid: Array<MapTile>, data: RoomData, block: IntArray): Boolean =
        (block.indices step COORDS_PER_CELL).all { slot -> cellAllows(grid, data, block[slot], block[slot + 1]) }

    private fun cellAllows(grid: Array<MapTile>, data: RoomData, col: Int, row: Int): Boolean {
        if (!MapGrid.inRange(col, row)) return false
        val tile = grid[MapGrid.index(col, row)]
        return tile !is MapTile.Room || tile.data === data
    }

    private fun crossesDoor(block: IntArray): Boolean {
        val cells = (block.indices step COORDS_PER_CELL).map { block[it] to block[it + 1] }.toHashSet()
        return cells.any { (col, row) ->
            (col + CELL_STRIDE to row) in cells && detectDoor(col + 1, row) != null ||
                (col to row + CELL_STRIDE) in cells && detectDoor(col, row + 1) != null
        }
    }

    private fun resolveGap(grid: Array<MapTile>, col: Int, row: Int): MapTile? {
        val colOdd = isOdd(col)
        val rowOdd = isOdd(row)
        return when {
            colOdd && rowOdd -> resolveDiagonal(grid, col, row)
            colOdd -> resolveNeighbors(roomAt(grid, col - 1, row), roomAt(grid, col + 1, row), col, row)
            else -> resolveNeighbors(roomAt(grid, col, row - 1), roomAt(grid, col, row + 1), col, row)
        }
    }

    private fun isOdd(value: Int): Boolean = value % CELL_STRIDE != 0

    private fun roomAt(grid: Array<MapTile>, col: Int, row: Int): MapTile.Room? =
        if (!MapGrid.inRange(col, row)) null else grid.getOrNull(MapGrid.index(col, row)) as? MapTile.Room

    private fun resolveDiagonal(grid: Array<MapTile>, col: Int, row: Int): MapTile? {
        val corners = listOf(
            roomAt(grid, col - 1, row - 1), roomAt(grid, col + 1, row - 1),
            roomAt(grid, col - 1, row + 1), roomAt(grid, col + 1, row + 1)
        )
        val first = corners.firstOrNull() ?: return null
        val shared = corners.all { it != null && it.data === first.data }
        return if (shared) MapTile.Connection(first.data) else null
    }

    private fun resolveNeighbors(roomA: MapTile.Room?, roomB: MapTile.Room?, col: Int, row: Int): MapTile? {
        if (roomA == null && roomB == null) return null
        if (roomA != null && roomB != null && roomA.data === roomB.data) return MapTile.Connection(roomA.data)
        return detectDoor(col, row)
    }

    private fun detectDoor(col: Int, row: Int): MapTile.Door? {
        val world = mc.level ?: return null
        val x = MapGrid.worldX(col)
        val z = MapGrid.worldZ(row)
        if (world.chunkSource.getChunk(x shr CHUNK_SHIFT, z shr CHUNK_SHIFT, false) == null) return null
        return DungeonDoorDetector.detect(x, z) { world.getBlockState(it) }
    }
}
