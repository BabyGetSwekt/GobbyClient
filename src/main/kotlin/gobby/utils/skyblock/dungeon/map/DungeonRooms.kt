package gobby.utils.skyblock.dungeon.map

import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_STRIDE

object DungeonRooms {

    private val STEP_DIRECTIONS = listOf(intArrayOf(0, -2), intArrayOf(0, 2), intArrayOf(2, 0), intArrayOf(-2, 0))
    private val NEIGHBOUR_ROOM_OFFSETS = (-CELL_STRIDE..CELL_STRIDE step CELL_STRIDE)
        .flatMap { dc -> (-CELL_STRIDE..CELL_STRIDE step CELL_STRIDE).map { dr -> dc to dr } }

    fun roomCellAt(grid: Array<MapTile>, x: Double, z: Double): Int? =
        containingRoomCell(grid, x, z) ?: neighbouringRoomCell(grid, MapGrid.roomColOf(x), MapGrid.roomRowOf(z))

    fun containingRoomCell(grid: Array<MapTile>, x: Double, z: Double): Int? =
        MapGrid.roomCellOf(x, z)?.takeIf { grid.getOrNull(it) is MapTile.Room }

    private fun neighbouringRoomCell(grid: Array<MapTile>, col: Int, row: Int): Int? =
        NEIGHBOUR_ROOM_OFFSETS.firstNotNullOfOrNull { (dc, dr) ->
            val neighbourCol = col + dc
            val neighbourRow = row + dr
            MapGrid.index(neighbourCol, neighbourRow)
                .takeIf { MapGrid.inRange(neighbourCol, neighbourRow) && grid.getOrNull(it) is MapTile.Room }
        }

    fun component(grid: Array<MapTile>, start: Int): Set<Int> {
        val seen = hashSetOf(start)
        val queue = ArrayDeque<Int>().apply { add(start) }
        while (queue.isNotEmpty()) expand(grid, queue.removeFirst(), seen, queue)
        return seen
    }

    fun canonical(grid: Array<MapTile>, cell: Int): Int = component(grid, cell).minOrNull() ?: cell

    private fun expand(grid: Array<MapTile>, cell: Int, seen: HashSet<Int>, queue: ArrayDeque<Int>) {
        STEP_DIRECTIONS.forEach { dir ->
            val col = MapGrid.col(cell) + dir[0]
            val row = MapGrid.row(cell) + dir[1]
            if (!MapGrid.inRange(col, row)) return@forEach
            val mid = grid[MapGrid.index(MapGrid.col(cell) + dir[0] / 2, MapGrid.row(cell) + dir[1] / 2)]
            val far = MapGrid.index(col, row)
            if (mid is MapTile.Connection && grid[far] is MapTile.Room && seen.add(far)) queue.add(far)
        }
    }
}
