package gobby.pathfinder.navigation

import gobby.utils.skyblock.dungeon.map.DungeonDoorDetector
import gobby.utils.skyblock.dungeon.map.MapConstants
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import kotlin.math.abs

internal object DungeonLandingBlacklist {

    fun forGrid(grid: Array<MapTile>): (BlockPos) -> Boolean = { position -> isLandable(grid, position) }

    fun isLandable(grid: Array<MapTile>, position: BlockPos): Boolean =
        position.y != DungeonDoorDetector.DOOR_ARCH_Y || !onDoorwayCeiling(grid, position)

    private fun onDoorwayCeiling(grid: Array<MapTile>, position: BlockPos): Boolean {
        val x = position.x + BLOCK_CENTER
        val z = position.z + BLOCK_CENTER
        val col = MapGrid.roomColOf(x)
        val row = MapGrid.roomRowOf(z)
        if (!MapGrid.inRange(col, row) || insideRoomFootprint(x, z, col, row)) return false
        val seam = MapGrid.cellOf(x, z) ?: return false
        return grid.getOrNull(seam) is MapTile.Door
    }

    private fun insideRoomFootprint(x: Double, z: Double, col: Int, row: Int): Boolean =
        abs(x - MapGrid.worldX(col)) <= ROOM_EXTENT && abs(z - MapGrid.worldZ(row)) <= ROOM_EXTENT

    private const val BLOCK_CENTER = 0.5
    private const val ROOM_EXTENT = MapConstants.HALF_ROOM - 1.0
}
