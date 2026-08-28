package gobby.utils.skyblock.dungeon.map

import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_STRIDE
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.RoomType

private const val COORDS_PER_CELL = 2
const val UNKNOWN_ROOM_NAME = ""

enum class SeamState { CONNECTED, BLOCKED, UNKNOWN }

private const val MAX_ROOM_CELLS = 4

internal object RoomInference {

    fun infer(grid: Array<MapTile>, seamAt: (Int, Int) -> SeamState, mapHasRoom: (Int, Int) -> Boolean?) {
        while (inferencePass(grid, seamAt, mapHasRoom)) continue
    }

    fun fillUnscanned(grid: Array<MapTile>, seamAt: (Int, Int) -> SeamState, mapHasRoom: (Int, Int) -> Boolean) {
        MapGrid.roomCells.forEach { cell ->
            if (grid[cell.index] !is MapTile.Empty || !mapHasRoom(cell.col, cell.row)) return@forEach
            val linked = unscannedComponent(grid, cell, seamAt, mapHasRoom)
            if (linked.size > MAX_ROOM_CELLS) {
                linked.forEach { grid[it] = MapTile.Room(unknownRoom(1), MapConstants.UNKNOWN_CORE) }
                return@forEach
            }
            val data = unknownRoom(linked.size)
            linked.forEach { grid[it] = MapTile.Room(data, MapConstants.UNKNOWN_CORE) }
        }
    }

    private fun unscannedComponent(
        grid: Array<MapTile>,
        start: GridCell,
        seamAt: (Int, Int) -> SeamState,
        mapHasRoom: (Int, Int) -> Boolean
    ): Set<Int> {
        val found = linkedSetOf(start.index)
        val queue = ArrayDeque(listOf(start.col to start.row))
        while (queue.isNotEmpty()) {
            val (col, row) = queue.removeFirst()
            neighbours(col, row).forEach { (target, gap) ->
                val (nextCol, nextRow) = target
                if (!MapGrid.inRange(nextCol, nextRow)) return@forEach
                val index = MapGrid.index(nextCol, nextRow)
                if (index in found || grid[index] !is MapTile.Empty) return@forEach
                if (!mapHasRoom(nextCol, nextRow) || seamAt(gap.first, gap.second) != SeamState.CONNECTED) return@forEach
                found += index
                queue += nextCol to nextRow
            }
        }
        return found
    }

    private fun unknownRoom(cells: Int): RoomData =
        RoomData(UNKNOWN_ROOM_NAME, RoomType.NORMAL, emptyList(), 0, 0, 0, shapeFor(cells))

    private fun shapeFor(size: Int): String = when (size) {
        2 -> "1x2"
        3 -> "L"
        4 -> "2x2"
        else -> "1x1"
    }

    private fun neighbours(col: Int, row: Int): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> = listOf(
        (col - CELL_STRIDE to row) to (col - 1 to row),
        (col + CELL_STRIDE to row) to (col + 1 to row),
        (col to row - CELL_STRIDE) to (col to row - 1),
        (col to row + CELL_STRIDE) to (col to row + 1)
    )

    private fun inferencePass(grid: Array<MapTile>, seamAt: (Int, Int) -> SeamState, mapHasRoom: (Int, Int) -> Boolean?): Boolean =
        MapGrid.roomCells.fold(false) { changed, cell -> inferCell(grid, cell, seamAt, mapHasRoom) || changed }

    private fun inferCell(grid: Array<MapTile>, cell: GridCell, seamAt: (Int, Int) -> SeamState, mapHasRoom: (Int, Int) -> Boolean?): Boolean {
        val room = grid[cell.index] as? MapTile.Room ?: return false
        val known = cluster(grid, cell, room.data, seamAt)
        if (known.size >= shapeSize(room.data.shape)) return false
        val arrangements = getArrangements(room.data.shape, cell.col, cell.row) ?: return false
        val block = selectArrangement(grid, room.data, known, arrangements, mapHasRoom) ?: return false
        return fillEmptyCells(grid, room.data, block).isNotEmpty()
    }

    private fun selectArrangement(
        grid: Array<MapTile>,
        data: RoomData,
        known: Set<Int>,
        arrangements: List<IntArray>,
        mapHasRoom: (Int, Int) -> Boolean?
    ): IntArray? {
        val valid = arrangements.filter { candidate ->
            blockIndices(candidate).containsAll(known) &&
                isValidBlock(grid, data, candidate)
        }
        val mapMatches = valid.filter { matchesMap(it, mapHasRoom) }
        return when {
            mapMatches.size == 1 -> mapMatches.single()
            valid.size == 1 -> valid.single()
            else -> null
        }
    }

    private fun shapeSize(shape: String): Int = when (shape) {
        "1x2" -> 2
        "1x3", "L" -> 3
        "1x4", "2x2" -> 4
        else -> 1
    }

    private fun cluster(grid: Array<MapTile>, start: GridCell, data: RoomData, seamAt: (Int, Int) -> SeamState): Set<Int> {
        val found = linkedSetOf(start.index)
        val queue = ArrayDeque(listOf(start.col to start.row))
        while (queue.isNotEmpty()) {
            val (col, row) = queue.removeFirst()
            neighbours(col, row).forEach { (target, gap) ->
                val (nextCol, nextRow) = target
                if (!MapGrid.inRange(nextCol, nextRow)) return@forEach
                val index = MapGrid.index(nextCol, nextRow)
                if (index in found || seamAt(gap.first, gap.second) != SeamState.CONNECTED) return@forEach
                if ((grid[index] as? MapTile.Room)?.data === data) {
                    found += index
                    queue += nextCol to nextRow
                }
            }
        }
        return found
    }

    private fun blockIndices(block: IntArray): List<Int> =
        (block.indices step COORDS_PER_CELL).map { MapGrid.index(block[it], block[it + 1]) }

    private fun matchesMap(block: IntArray, mapHasRoom: (Int, Int) -> Boolean?): Boolean =
        (block.indices step COORDS_PER_CELL).all { slot ->
            mapHasRoom(block[slot], block[slot + 1]) != false
        }

    private fun fillEmptyCells(grid: Array<MapTile>, data: RoomData, block: IntArray): List<Int> =
        blockIndices(block).filter { isMissing(grid[it]) }
            .onEach { grid[it] = MapTile.Room(data, MapConstants.UNKNOWN_CORE) }

    private fun isMissing(tile: MapTile): Boolean = when (tile) {
        MapTile.Empty -> true
        is MapTile.Room -> tile.data.name == UNKNOWN_ROOM_NAME
        else -> false
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
        return tile !is MapTile.Room || tile.data === data || tile.data.name == UNKNOWN_ROOM_NAME
    }

}
