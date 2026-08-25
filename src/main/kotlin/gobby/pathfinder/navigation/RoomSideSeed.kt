package gobby.pathfinder.navigation

import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapConstants.HALF_ROOM
import net.minecraft.core.BlockPos

internal object RoomSideSeed {
    fun find(doorCell: Int, roomCell: Int): BlockPos {
        val roomCol = MapGrid.col(roomCell)
        val roomRow = MapGrid.row(roomCell)
        val offsetX = Integer.signum(MapGrid.col(doorCell) - roomCol) * EDGE_OFFSET
        val offsetZ = Integer.signum(MapGrid.row(doorCell) - roomRow) * EDGE_OFFSET
        return BlockPos(
            MapGrid.worldX(roomCol) + offsetX,
            DungeonRoomCoordinates.DOOR_Y,
            MapGrid.worldZ(roomRow) + offsetZ
        )
    }

    private const val EDGE_OFFSET = HALF_ROOM - 1
}
