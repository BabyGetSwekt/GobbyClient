package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.RoomStep
import gobby.pathfinder.etherwarp.SearchDeadline
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos

internal class AtlasRoomPreparer(
    private val grid: Array<MapTile>,
    private val snapshot: BlockCache.SnapshotView,
    private val deadline: SearchDeadline
) {
    val runtimeIndex = RoomLandingIndex(snapshot, System.nanoTime() + deadline.remainingNanos, MAX_RUNTIME_PER_SEED, MAX_RUNTIME_TOTAL)

    fun prepare(rooms: List<RoomStep>): AtlasRoomPreparation? {
        val portals = buildPortals(rooms) ?: return null
        val prepared = ArrayList<PreparedGraphRoom>(rooms.size)
        rooms.forEach { room ->
            if (deadline.expired) return null
            prepared += prepareRoom(room, portals)
        }
        if (prepared.any { it.positions.isEmpty() && !it.runtimeBridge }) return null
        return AtlasRoomPreparation(prepared, portals)
    }

    private fun prepareRoom(step: RoomStep, portals: List<PreparedPortal>): PreparedGraphRoom {
        val component = DungeonRooms.component(grid, step.cellIndex)
        val seeds = runtimeSeeds(step, component, portals)
        preparedAtlasRoom(step, grid, snapshot)?.let { prepared ->
            val portalCount = portals.count { it.fromCanonical == step.cellIndex || it.toCanonical == step.cellIndex }
            return prepared.copy(
                positions = (prepared.positions + doorwayLandings(component, seeds)).distinct(),
                runtimeBridge = portalCount >= MULTI_PORTAL_ROOM,
                runtimeSeeds = seeds
            )
        }
        if (deadline.expired) return PreparedGraphRoom(step.cellIndex, step.data.name, emptyList(), emptyList(), emptyList(), runtimeBridge = true)
        val candidates = runtimeIndex.queryRoom(component, grid, seeds)
        return PreparedGraphRoom(step.cellIndex, step.data.name, candidates, candidates.take(MAX_RUNTIME_ANCHORS), emptyList(), runtimeBridge = true, runtimeSeeds = seeds)
    }

    private fun doorwayLandings(component: Set<Int>, seeds: List<BlockPos>): List<BlockPos> =
        if (deadline.expired || seeds.isEmpty()) emptyList() else runtimeIndex.queryRoom(component, grid, seeds)

    private fun buildPortals(rooms: List<RoomStep>): List<PreparedPortal>? {
        val result = ArrayList<PreparedPortal>(rooms.lastIndex)
        for (index in 0 until rooms.lastIndex) {
            if (deadline.expired) return null
            val first = rooms[index]
            val second = rooms[index + 1]
            val fromSeed = if (index == 0) roomSideSeed(first.doorCell, first.cellIndex) else doorPosition(first.doorCell, first.cellIndex)
            val toSeed = roomSideSeed(first.doorCell, second.cellIndex)
            val fromComponent = DungeonRooms.component(grid, first.cellIndex)
            val toComponent = DungeonRooms.component(grid, second.cellIndex)
            result += PreparedPortal(
                first.cellIndex, second.cellIndex, first.data.name, second.data.name, fromSeed, toSeed,
                RoomPortalCandidateResolver.find(first, fromComponent, fromSeed, grid, snapshot, runtimeIndex),
                RoomPortalCandidateResolver.find(second, toComponent, toSeed, grid, snapshot, runtimeIndex)
            )
        }
        return result
    }

    private fun runtimeSeeds(step: RoomStep, component: Set<Int>, portals: List<PreparedPortal>): List<BlockPos> {
        val seeds = LinkedHashSet<BlockPos>()
        portals.forEach { portal ->
            if (portal.fromCanonical == step.cellIndex) seeds += portal.fromSeed
            if (portal.toCanonical == step.cellIndex) seeds += portal.toSeed
        }
        component.forEach { cell -> seeds += doorPosition(null, cell) }
        return seeds.toList()
    }

    private fun doorPosition(doorCell: Int?, roomCell: Int): BlockPos {
        val cell = doorCell ?: roomCell
        return BlockPos(MapGrid.worldX(MapGrid.col(cell)), DungeonRoomCoordinates.DOOR_Y, MapGrid.worldZ(MapGrid.row(cell)))
    }

    private fun roomSideSeed(doorCell: Int?, roomCell: Int): BlockPos {
        val door = doorCell ?: return doorPosition(null, roomCell)
        val component = DungeonRooms.component(grid, roomCell)
        val col = MapGrid.col(door)
        val row = MapGrid.row(door)
        val neighbours = if (col and 1 == 1) listOf(MapGrid.index(col - 1, row), MapGrid.index(col + 1, row))
        else listOf(MapGrid.index(col, row - 1), MapGrid.index(col, row + 1))
        val side = neighbours.firstOrNull { it == roomCell } ?: neighbours.firstOrNull { it in component } ?: roomCell
        return RoomSideSeed.find(door, side)
    }

    companion object {
        fun preparedAtlasRoom(step: RoomStep, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView): PreparedGraphRoom? {
            val component = DungeonRooms.component(grid, step.cellIndex)
            val prepared = CompiledRoomLandingAtlas.candidates(step.data, component, grid, snapshot)
            if (!prepared.compatible) return null
            return PreparedGraphRoom(step.cellIndex, step.data.name, prepared.positions, prepared.anchors, prepared.edges, prepared.outgoing)
        }

        private const val MAX_RUNTIME_ANCHORS = 8
        private const val MAX_RUNTIME_PER_SEED = 3
        private const val MAX_RUNTIME_TOTAL = 24
        private const val MULTI_PORTAL_ROOM = 2
    }
}
