package gobby.utils.skyblock.dungeon.map

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import net.minecraft.core.BlockPos

object MapDoors {

    private const val DOOR_WITHER = 119
    private const val DOOR_BLOOD = 18
    private const val EMPTY = 0
    private const val DOOR_CLEAR_MIN_Y = 69
    private const val DOOR_CLEAR_MAX_Y = 72
    private val DOOR_WIDTH_RANGE = -1..1

    fun updateFromMap(grid: Array<MapTile>, opened: BooleanArray) {
        grid.indices.forEach { cell ->
            val col = cell % GRID_SIZE
            val row = cell / GRID_SIZE
            if ((col and 1 == 1) == (row and 1 == 1)) return@forEach
            val byte = MapCheckmarks.doorByte(col, row)?.toInt() ?: return@forEach
            val existing = grid[cell] as? MapTile.Door
            if (existing?.type == DoorType.NORMAL) setDoorPassable(col, row, passable = true)
            when (byte) {
                DOOR_WITHER -> lockDoor(grid, cell, col, row, DoorType.WITHER, existing, opened)
                DOOR_BLOOD -> lockDoor(grid, cell, col, row, DoorType.BLOOD, existing, opened)
                EMPTY -> Unit
                else -> if (!opened[cell] && existing != null) {
                    opened[cell] = true
                    setDoorPassable(col, row, passable = true)
                }
            }
        }
    }

    private fun lockDoor(grid: Array<MapTile>, cell: Int, col: Int, row: Int, type: DoorType, existing: MapTile.Door?, opened: BooleanArray) {
        if (existing?.type != type) grid[cell] = MapTile.Door(type)
        opened[cell] = false
        setDoorPassable(col, row, passable = false)
    }

    private fun setDoorPassable(col: Int, row: Int, passable: Boolean) {
        val x = MapGrid.worldX(col)
        val z = MapGrid.worldZ(row)
        val horizontal = col and 1 == 1
        (DOOR_CLEAR_MIN_Y..DOOR_CLEAR_MAX_Y).flatMap { y ->
            DOOR_WIDTH_RANGE.map { off -> if (horizontal) BlockPos(x, y, z + off) else BlockPos(x + off, y, z) }
        }.forEach { if (passable) BlockCache.markPassable(it) else BlockCache.unmarkPassable(it) }
    }
}
