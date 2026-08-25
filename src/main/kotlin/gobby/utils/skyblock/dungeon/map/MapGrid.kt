package gobby.utils.skyblock.dungeon.map

import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_STRIDE
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.map.MapConstants.HALF_ROOM
import gobby.utils.skyblock.dungeon.map.MapConstants.ROOM_STRIDE
import gobby.utils.skyblock.dungeon.map.MapConstants.START_X
import gobby.utils.skyblock.dungeon.map.MapConstants.START_Z
import gobby.utils.skyblock.dungeon.map.MapConstants.STEP
import kotlin.math.roundToInt

data class GridCell(val index: Int, val col: Int, val row: Int)

object MapGrid {
    fun index(col: Int, row: Int): Int = row * GRID_SIZE + col

    private val columnMajorCells: List<GridCell> = List(GRID_SIZE * GRID_SIZE) { ordinal ->
        val col = ordinal / GRID_SIZE
        val row = ordinal % GRID_SIZE
        GridCell(index(col, row), col, row)
    }

    private fun isRoomCell(cell: GridCell): Boolean = cell.col % CELL_STRIDE == 0 && cell.row % CELL_STRIDE == 0

    val roomCells: List<GridCell> = columnMajorCells.filter(::isRoomCell)

    val gapCells: List<GridCell> = columnMajorCells.filterNot(::isRoomCell)

    fun col(index: Int): Int = index % GRID_SIZE

    fun row(index: Int): Int = index / GRID_SIZE

    fun inRange(col: Int, row: Int): Boolean = col in 0 until GRID_SIZE && row in 0 until GRID_SIZE

    fun worldX(col: Int): Int = START_X + col * HALF_ROOM

    fun worldZ(row: Int): Int = START_Z + row * HALF_ROOM

    fun colOf(x: Double): Int = ((x - START_X) / HALF_ROOM).roundToInt()

    fun rowOf(z: Double): Int = ((z - START_Z) / HALF_ROOM).roundToInt()

    fun cellOf(x: Double, z: Double): Int? {
        val col = colOf(x)
        val row = rowOf(z)
        return if (inRange(col, row)) index(col, row) else null
    }

    fun roomColOf(x: Double): Int = ((x - START_X) / ROOM_STRIDE).roundToInt() * CELL_STRIDE

    fun roomRowOf(z: Double): Int = ((z - START_Z) / ROOM_STRIDE).roundToInt() * CELL_STRIDE

    fun roomCellOf(x: Double, z: Double): Int? {
        val col = roomColOf(x)
        val row = roomRowOf(z)
        return if (inRange(col, row)) index(col, row) else null
    }

    fun screenX(col: Int): Int = (col / CELL_STRIDE) * STEP

    fun screenY(row: Int): Int = (row / CELL_STRIDE) * STEP

    private const val BOUNDS_MARGIN = HALF_ROOM * 2

    fun dungeonChunkBounds(): IntArray = intArrayOf(
        (worldX(0) - BOUNDS_MARGIN) shr 4, (worldZ(0) - BOUNDS_MARGIN) shr 4,
        (worldX(GRID_SIZE - 1) + BOUNDS_MARGIN) shr 4, (worldZ(GRID_SIZE - 1) + BOUNDS_MARGIN) shr 4
    )
}
