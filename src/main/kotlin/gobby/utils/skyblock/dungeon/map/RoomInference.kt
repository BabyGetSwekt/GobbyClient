package gobby.utils.skyblock.dungeon.map

import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_STRIDE
import gobby.utils.skyblock.dungeon.tiles.RoomData

private const val COORDS_PER_CELL = 2

internal object RoomInference {

    fun infer(grid: Array<MapTile>, isRoomFloor: (Int, Int) -> Boolean) {
        while (inferencePass(grid, isRoomFloor)) continue
    }

    private fun inferencePass(grid: Array<MapTile>, isRoomFloor: (Int, Int) -> Boolean): Boolean =
        MapGrid.roomCells.fold(false) { changed, cell -> inferCell(grid, cell, isRoomFloor) || changed }

    private fun inferCell(grid: Array<MapTile>, cell: GridCell, isRoomFloor: (Int, Int) -> Boolean): Boolean {
        val room = grid[cell.index] as? MapTile.Room ?: return false
        val known = cluster(grid, cell, room.data)
        if (known.size >= shapeSize(room.data.shape)) return false
        val arrangements = getArrangements(room.data.shape, cell.col, cell.row) ?: return false
        val block = arrangements.singleOrNull { candidate ->
            blockIndices(candidate).containsAll(known) &&
                isValidBlock(grid, room.data, candidate) &&
                !wouldBlockOtherRoom(grid, room.data, candidate) &&
                gapsAreRoomFloor(candidate, isRoomFloor)
        } ?: return false
        return fillEmptyCells(grid, room.data, block).isNotEmpty()
    }

    private fun shapeSize(shape: String): Int = when (shape) {
        "1x2" -> 2
        "1x3", "L" -> 3
        "1x4", "2x2" -> 4
        else -> 1
    }

    private fun cluster(grid: Array<MapTile>, start: GridCell, data: RoomData): Set<Int> {
        val found = linkedSetOf(start.index)
        val queue = ArrayDeque(listOf(start.col to start.row))
        while (queue.isNotEmpty()) {
            val (col, row) = queue.removeFirst()
            listOf(col - CELL_STRIDE to row, col + CELL_STRIDE to row, col to row - CELL_STRIDE, col to row + CELL_STRIDE)
                .filter { (c, r) -> MapGrid.inRange(c, r) }
                .forEach { (c, r) ->
                    val index = MapGrid.index(c, r)
                    if (index !in found && (grid[index] as? MapTile.Room)?.data === data) {
                        found += index
                        queue += c to r
                    }
                }
        }
        return found
    }

    private fun blockIndices(block: IntArray): List<Int> =
        (block.indices step COORDS_PER_CELL).map { MapGrid.index(block[it], block[it + 1]) }

    private fun fillEmptyCells(grid: Array<MapTile>, data: RoomData, block: IntArray): List<Int> =
        blockIndices(block).filter { grid[it] is MapTile.Empty }
            .onEach { grid[it] = MapTile.Room(data, MapConstants.UNKNOWN_CORE) }


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


    private fun buildLinearArrangements(size: Int, col: Int, row: Int): List<IntArray> =
        (0 until size).map { offset -> horizontalRun(size, col - offset * CELL_STRIDE, row) } +
            (0 until size).map { offset -> verticalRun(size, col, row - offset * CELL_STRIDE) }

    private fun horizontalRun(size: Int, startCol: Int, row: Int): IntArray =
        IntArray(size * COORDS_PER_CELL) { slot -> if (isColumnSlot(slot)) startCol + cellOfSlot(slot) * CELL_STRIDE else row }

    private fun verticalRun(size: Int, col: Int, startRow: Int): IntArray =
        IntArray(size * COORDS_PER_CELL) { slot -> if (isColumnSlot(slot)) col else startRow + cellOfSlot(slot) * CELL_STRIDE }

    private fun isColumnSlot(slot: Int): Boolean = slot % COORDS_PER_CELL == 0

    private fun cellOfSlot(slot: Int): Int = slot / COORDS_PER_CELL


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

    private fun gapsAreRoomFloor(block: IntArray, isRoomFloor: (Int, Int) -> Boolean): Boolean {
        val cells = (block.indices step COORDS_PER_CELL).map { block[it] to block[it + 1] }.toHashSet()
        return cells.all { (col, row) ->
            ((col + CELL_STRIDE to row) !in cells || isRoomFloor(col + 1, row)) &&
                ((col to row + CELL_STRIDE) !in cells || isRoomFloor(col, row + 1))
        }
    }
}
