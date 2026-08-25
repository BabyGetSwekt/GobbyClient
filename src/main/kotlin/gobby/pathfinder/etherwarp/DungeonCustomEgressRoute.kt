package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.CustomRoomLandings
import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.search.SearchLane
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomData
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object DungeonCustomEgressRoute {
    fun find(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        enterRoom: Boolean,
        startCell: Int?,
        goalCell: Int?,
        grid: Array<MapTile>,
        opened: (Int) -> Boolean,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long,
        deadline: SearchDeadline
    ): List<EtherwarpNode>? {
        if (kind != EtherwarpKind.ETHERWARP) return null
        val start = startCell ?: return null
        val target = goalCell ?: return null
        val egress = resolve(start, grid, snapshot) ?: return null
        if (!EtherwarpUtils.isEtherwarpable(egress, cached = true, snapshot = snapshot)) return null
        if (BlockPos.containing(from).below() == egress) return null
        if (start == target) return null
        val prefix = EtherwarpPathfinder.search(
            from,
            egress,
            kind,
            config,
            { it == egress },
            snapshot,
            deadline,
            SearchLane.PRIMITIVE_ONLY
        )
            ?: return null
        val suffix = DungeonEtherwarpPathfinder.findDungeonPath(
            prefix.last().eye, goal, kind, config, enterRoom, grid, opened, snapshot, mapRevision, deadline
        ) ?: return null
        return prefix + suffix.drop(FIRST_NODE_INDEX)
    }

    private fun resolve(cell: Int, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView): BlockPos? {
        val room = grid.getOrNull(cell) as? MapTile.Room ?: return null
        val local = CustomRoomLandings.egress(room.data) ?: return null
        return CustomRoomLandings.resolve(room.data, DungeonRooms.component(grid, cell), grid, snapshot, local)
    }

    private const val FIRST_NODE_INDEX = 1
}

internal fun canHaveCustomEgress(data: RoomData): Boolean = data.shape == EGRESS_SHAPE &&
    (data.name == HIGHER_BLAZE || data.name == WATER_BOARD || data.name == LOWER_BLAZE) ||
    data.name == SLIME && data.shape == SLIME_SHAPE

private const val HIGHER_BLAZE = "Higher Blaze"
private const val WATER_BOARD = "Water Board"
private const val LOWER_BLAZE = "Lower Blaze"
private const val SLIME = "Slime"
private const val EGRESS_SHAPE = "1x1"
private const val SLIME_SHAPE = "1x3"
