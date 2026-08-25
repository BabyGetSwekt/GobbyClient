package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.AtlasRoomRoutePlanner
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object DungeonSameRoomRoute {
    fun find(
        from: Vec3,
        goalBlock: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        startCell: Int?,
        goalCell: Int?,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView
    ): List<EtherwarpNode>? {
        val start = startCell ?: return null
        val goal = goalCell ?: return null
        val startRoom = grid.getOrNull(start) as? MapTile.Room ?: return null
        val room = grid.getOrNull(goal) as? MapTile.Room ?: return null
        if (startRoom.data != room.data || startRoom.core != room.core) return null
        return AtlasRoomRoutePlanner.findPrepared(
            from,
            goalBlock,
            kind,
            config,
            RoomStep(room.data, goal, null),
            grid,
            snapshot
        )?.nodes
    }
}
