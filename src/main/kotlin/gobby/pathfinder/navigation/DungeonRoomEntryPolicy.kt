package gobby.pathfinder.navigation

import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomType

internal object DungeonRoomEntryPolicy {
    fun canTeleportInto(grid: Array<MapTile>, cell: Int): Boolean {
        val data = (grid.getOrNull(cell) as? MapTile.Room)?.data ?: return true
        return when (data.type) {
            RoomType.TRAP -> false
            RoomType.PUZZLE -> data.name in ETHERWARPABLE_PUZZLE_ROOMS
            else -> true
        }
    }

    private val ETHERWARPABLE_PUZZLE_ROOMS = setOf(
        "Creeper Beams", "Higher Blaze", "Ice Fill", "Ice Path",
        "Lower Blaze", "Quiz", "Three Weirdos", "Tic Tac Toe", "Water Board"
    )
}
