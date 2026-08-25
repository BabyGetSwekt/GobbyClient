package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.navigation.CustomRoomLandings
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal data class DungeonSearchSetup(
    val from: Vec3,
    val goalBlock: BlockPos,
    val kind: EtherwarpKind,
    val config: EtherwarpPathConfig,
    val enterRoom: Boolean,
    val cache: BlockCache.SnapshotView,
    val deadline: SearchDeadline,
    val fallbackDeadline: SearchDeadline,
    val startCell: Int?,
    val goalCell: Int?,
    val mapRevision: Long,
    val target: BlockPos,
    val exactGoal: Boolean
)

internal fun prepareDungeonSearch(
    from: Vec3,
    to: BlockPos,
    kind: EtherwarpKind,
    config: EtherwarpPathConfig,
    enterRoom: Boolean,
    grid: Array<MapTile>,
    snapshot: BlockCache.SnapshotView?,
    mapRevision: Long,
    deadline: SearchDeadline?,
    onTimeout: () -> Unit
): DungeonSearchSetup? {
    if (Thread.currentThread().isInterrupted) return null
    val searchDeadline = deadline ?: SearchDeadline(config.timeout)
    if (searchDeadline.expired) {
        onTimeout()
        return null
    }
    val cache = (snapshot ?: BlockCache.freeze()).let { if (it.isTracking) it else it.trackingMissingChunks() }
    val startCell = DungeonRooms.roomCellAt(grid, from.x, from.z)
    val goalCell = DungeonRooms.containingRoomCell(grid, to.x + 0.5, to.z + 0.5)
        ?: DungeonRooms.roomCellAt(grid, to.x + 0.5, to.z + 0.5)
    if (searchDeadline.expired) {
        onTimeout()
        return null
    }
    val exactGoal = enterRoom && goalCell?.let { (grid.getOrNull(it) as? MapTile.Room)?.data }?.let(CustomRoomLandings::target) != null
    return DungeonSearchSetup(from, kind.goal(to), kind, config, enterRoom, cache, searchDeadline, SearchDeadline(config.timeout * FALLBACK_BUDGET_FACTOR), startCell, goalCell, mapRevision, to, exactGoal)
}

private const val FALLBACK_BUDGET_FACTOR = 3L
