package gobby.utils.skyblock.dungeon.map

object DungeonRooms {

    private val STEP_DIRECTIONS = listOf(intArrayOf(0, -2), intArrayOf(0, 2), intArrayOf(2, 0), intArrayOf(-2, 0))
    private val SNAP_OFFSETS = (-1..1).flatMap { dc -> (-1..1).map { dr -> dc to dr } }

    fun roomCellAt(grid: Array<MapTile>, x: Double, z: Double): Int? {
        val col = MapGrid.colOf(x)
        val row = MapGrid.rowOf(z)
        return SNAP_OFFSETS.firstNotNullOfOrNull { (dc, dr) ->
            val c = col + dc
            val r = row + dr
            MapGrid.index(c, r).takeIf { MapGrid.inRange(c, r) && grid.getOrNull(it) is MapTile.Room }
        }
    }

    fun containingRoomCell(grid: Array<MapTile>, x: Double, z: Double): Int? {
        val roomCol = MapGrid.colOf(x).let { it - (it and 1) }
        val roomRow = MapGrid.rowOf(z).let { it - (it and 1) }
        if (!MapGrid.inRange(roomCol, roomRow)) return null
        return MapGrid.index(roomCol, roomRow).takeIf { grid.getOrNull(it) is MapTile.Room }
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
