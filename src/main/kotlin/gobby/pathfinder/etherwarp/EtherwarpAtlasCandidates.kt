package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.DungeonRoomCoordinates
import gobby.pathfinder.navigation.PreparedDirectedEdge
import gobby.pathfinder.navigation.RoomLandingIndex
import gobby.pathfinder.navigation.RoomLandingResolver
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.map.MapGrid
import net.minecraft.core.BlockPos

internal data class EtherwarpAtlasCandidates(val positions: List<BlockPos>, val edges: List<PreparedDirectedEdge>)

internal fun atlasCandidates(handle: EtherwarpHopField.Handle): EtherwarpAtlasCandidates {
    val grid = handle.grid
    val cells = handle.allowedCells ?: grid.indices.toSet()
    val roomComponents = cells.asSequence().mapNotNull { cell ->
        (grid.getOrNull(cell) as? MapTile.Room)?.let { cell to it.data }
    }.map { (cell, data) -> DungeonRooms.component(grid, cell) to data }
        .distinctBy { it.first }
        .toList()
    val indexer = RoomLandingIndex(handle.snapshot, System.nanoTime() + DOORWAY_SCAN_BUDGET_NANOS)
    val rooms = roomComponents.map { (component, data) ->
        RoomLandingResolver.resolve(data, component, grid, handle.snapshot, indexer, doorSeeds(component, grid))
    }
    EtherwarpFieldDiagnostics.logCandidateSources(roomComponents, rooms)
    val doorways = doorwayLandings(handle, roomComponents.map { it.first }, indexer)
    return EtherwarpAtlasCandidates(
        (rooms.flatMap { it.positions } + doorways).distinct(),
        rooms.flatMap { it.edges }.distinctBy { it.from to it.to }
    )
}

private fun doorwayLandings(handle: EtherwarpHopField.Handle, components: List<Set<Int>>, indexer: RoomLandingIndex): List<BlockPos> {
    val grid = handle.grid
    return components.flatMap { component ->
        val seeds = doorSeeds(component, grid)
        if (seeds.isEmpty()) emptyList() else indexer.queryRoom(component, grid, seeds)
    }
}

private fun doorSeeds(component: Set<Int>, grid: Array<MapTile>): List<BlockPos> = component.flatMap { cell ->
    DOOR_OFFSETS.mapNotNull { (columnStep, rowStep) ->
        val column = MapGrid.col(cell) + columnStep
        val row = MapGrid.row(cell) + rowStep
        if (!MapGrid.inRange(column, row)) return@mapNotNull null
        val door = MapGrid.index(column, row)
        if (grid.getOrNull(door) !is MapTile.Door && grid.getOrNull(door) !is MapTile.Connection) return@mapNotNull null
        BlockPos(MapGrid.worldX(column), DungeonRoomCoordinates.DOOR_Y, MapGrid.worldZ(row))
    }
}

internal fun fallbackCandidates(handle: EtherwarpHopField.Handle): List<BlockPos> = handle.snapshot.knownChunkKeys().asSequence().flatMap { key ->
    val candidates = handle.snapshot.peekNonAirCandidates(key)
    if (candidates.none { allowedFor(it, handle.allowedCells) }) emptySequence()
    else handle.snapshot.nonAirCandidates(key).asSequence().filter { allowedFor(it, handle.allowedCells) }
}.toList()

private fun allowedFor(pos: BlockPos, allowedCells: Set<Int>?): Boolean = allowedCells?.let { cells ->
    MapGrid.roomCellOf(pos.x + BLOCK_CENTER, pos.z + BLOCK_CENTER) in cells
} ?: true

private val DOOR_OFFSETS = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)
private const val DOORWAY_SCAN_BUDGET_NANOS = 2_000_000_000L
private const val BLOCK_CENTER = 0.5
