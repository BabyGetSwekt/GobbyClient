package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.DungeonRoomCoordinates

import gobby.features.dungeons.DungeonMap
import gobby.pathfinder.navigation.PreparedDungeonRouteCache
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.DoorType
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object DungeonPreparedPathLookup {
    fun take(
        from: Vec3,
        to: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        enterRoom: Boolean,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long = DungeonMap.revision
    ): List<EtherwarpNode>? {
        val trackedSnapshot = if (snapshot.isTracking) snapshot else snapshot.trackingMissingChunks()
        val goal = kind.goal(to)
        val targetCell = DungeonRooms.containingRoomCell(grid, goal.x + CENTER, goal.z + CENTER)
            ?: DungeonRooms.roomCellAt(grid, goal.x + CENTER, goal.z + CENTER)
        return targetCell?.let { target ->
            PreparedDungeonRouteCache.take(from, goal, target, enterRoom, kind, config, trackedSnapshot, mapRevision)
        }
    }
}

internal fun dungeonDoorWorld(doorCell: Int, approachCell: Int, grid: Array<MapTile>): BlockPos {
    val x = MapGrid.worldX(MapGrid.col(doorCell))
    val z = MapGrid.worldZ(MapGrid.row(doorCell))
    val door = grid.getOrNull(doorCell) as? MapTile.Door
    if (door == null || (door.type != DoorType.WITHER && door.type != DoorType.BLOOD)) return BlockPos(x, DungeonRoomCoordinates.DOOR_Y, z)
    val dx = (MapGrid.col(approachCell) - MapGrid.col(doorCell)).coerceIn(-1, 1)
    val dz = (MapGrid.row(approachCell) - MapGrid.row(doorCell)).coerceIn(-1, 1)
    return BlockPos(x + dx * DOOR_APPROACH_OFFSET, DungeonRoomCoordinates.DOOR_Y, z + dz * DOOR_APPROACH_OFFSET)
}

internal fun doorLandingReached(position: BlockPos, goal: BlockPos): Boolean =
    position.x == goal.x && position.z == goal.z && position.y in goal.y..goal.y + 1

private const val CENTER = 0.5
private const val DOOR_APPROACH_OFFSET = 2
