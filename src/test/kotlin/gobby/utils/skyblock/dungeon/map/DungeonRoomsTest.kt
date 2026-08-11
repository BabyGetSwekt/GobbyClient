package gobby.utils.skyblock.dungeon.map

import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals

class DungeonRoomsTest {

    @Test
    fun resolvesRoomFromDiagonalConnectionCell() {
        val grid = Array<MapTile>(MapConstants.GRID_SIZE * MapConstants.GRID_SIZE) { MapTile.Empty }
        val room = MapTile.Room(RoomData("Room", RoomType.NORMAL, emptyList(), 0, 0, 0), 0)
        grid[MapGrid.index(2, 2)] = room

        assertEquals(MapGrid.index(2, 2), DungeonRooms.roomCellAt(grid, -169.0, -169.0))
    }
}
