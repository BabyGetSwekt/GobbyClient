package gobby.utils.skyblock.dungeon.map

import gobby.utils.skyblock.dungeon.tiles.RoomData

sealed class MapTile {
    data object Empty : MapTile()
    data class Room(val data: RoomData, val core: Int) : MapTile()
    data class Connection(val data: RoomData) : MapTile()
    data class Door(val type: DoorType) : MapTile()
}

enum class DoorType { NORMAL, WITHER, BLOOD, ENTRANCE }

enum class MapCheckmark { NONE, WHITE, GREEN, FAILED }

object MapConstants {
    const val GRID_SIZE = 11
    const val HALF_ROOM = 16
    const val START_X = -185
    const val START_Z = -185

    const val CELL_SIZE = 16
    const val GAP = 4
    const val STEP = CELL_SIZE + GAP
    const val DOOR_THICKNESS = 8

    const val CHECK_GREEN: Byte = 30
    const val CHECK_WHITE: Byte = 34
    const val CHECK_FAILED: Byte = 18
}
