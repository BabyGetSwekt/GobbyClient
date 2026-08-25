package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.DungeonRoomCoordinates
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.map.RoomGraph
import net.minecraft.core.BlockPos
import kotlin.math.abs

internal enum class SegmentGoalKind { FINAL_GOAL, ROOM_ENTRY, DOOR_APPROACH }

internal class DungeonSegmentGoal(
    val target: BlockPos,
    val goalKind: SegmentGoalKind,
    val door: BlockPos?,
    val reached: (BlockPos) -> Boolean
)

internal object DungeonSegmentGoals {

    fun resolve(
        step: RoomStep,
        next: RoomStep?,
        graph: RoomGraph,
        grid: Array<MapTile>,
        cache: BlockCache.SnapshotView,
        goalBlock: BlockPos,
        goalCells: Set<Int>,
        exactGoal: Boolean,
        allowEnter: Boolean
    ): DungeonSegmentGoal {
        val doorCell = step.doorCell
        val nextCells = next?.let { graph.component(it.cellIndex) }
        if (doorCell == null || nextCells == null) {
            return DungeonSegmentGoal(goalBlock, SegmentGoalKind.FINAL_GOAL, null, goalReached(goalBlock, goalCells, exactGoal))
        }
        val door = dungeonDoorWorld(doorCell, step.cellIndex, grid)
        if (!allowEnter) return DungeonSegmentGoal(door, SegmentGoalKind.DOOR_APPROACH, door) { doorApproachReached(it, door) }
        val entry = roomEntryGoal(doorCell, nextCells, grid, cache)
        return DungeonSegmentGoal(entry ?: door, SegmentGoalKind.ROOM_ENTRY, door, roomEntered(door, nextCells))
    }

    fun goalReached(goalBlock: BlockPos, goalCells: Set<Int>, exact: Boolean = false): (BlockPos) -> Boolean =
        { it == goalBlock || !exact && MapGrid.roomCellOf(it.x + BLOCK_CENTER, it.z + BLOCK_CENTER) in goalCells }

    fun roomEntered(door: BlockPos, nextCells: Set<Int>): (BlockPos) -> Boolean = { position ->
        MapGrid.roomCellOf(position.x + BLOCK_CENTER, position.z + BLOCK_CENTER) in nextCells &&
            abs(position.y - door.y) <= NEXT_ROOM_Y_TOLERANCE
    }

    fun doorApproachReached(position: BlockPos, door: BlockPos): Boolean =
        VecUtils.distanceSq(position, door) <= DOOR_APPROACH_RADIUS_SQUARED

    fun roomEntryGoal(doorCell: Int, nextCells: Set<Int>, grid: Array<MapTile>, cache: BlockCache.SnapshotView): BlockPos? {
        val entryCell = entryCellOf(doorCell, nextCells) ?: return null
        val center = BlockPos(
            MapGrid.worldX(MapGrid.col(entryCell)),
            DungeonRoomCoordinates.DOOR_BLOCK_SAMPLE_Y,
            MapGrid.worldZ(MapGrid.row(entryCell))
        )
        return EtherwarpUtils.nearestEtherwarpable(center, cached = true, snapshot = cache) {
            DungeonRooms.containingRoomCell(grid, it.x + BLOCK_CENTER, it.z + BLOCK_CENTER) in nextCells
        }
    }

    private fun entryCellOf(doorCell: Int, nextCells: Set<Int>): Int? {
        val col = MapGrid.col(doorCell)
        val row = MapGrid.row(doorCell)
        val neighbours = if (col and 1 == 1) listOf(MapGrid.index(col - 1, row), MapGrid.index(col + 1, row))
        else listOf(MapGrid.index(col, row - 1), MapGrid.index(col, row + 1))
        return neighbours.firstOrNull { it in nextCells }
    }

    const val BLOCK_CENTER = 0.5
    private const val NEXT_ROOM_Y_TOLERANCE = 3
    private const val DOOR_APPROACH_RADIUS_SQUARED = 9.0
}
