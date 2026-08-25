package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.ResolvedRoomLandings
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomData

internal object EtherwarpFieldDiagnostics {
    fun logBuild(handle: EtherwarpHopField.Handle, field: EtherwarpHopField.BuiltField?, started: Long, candidates: Int, candidateNanos: Long = 0L, builder: EtherwarpHopFieldBuilder? = null) {
        val elapsed = (System.nanoTime() - started) / NANOS_PER_MILLISECOND
        pathLog { "field goal=${handle.goal} cells=${handle.allowedCells?.size ?: 0} candidates=$candidates nodes=${field?.nodeCount ?: 0} byRoom=${field?.let { describeRooms(it, handle) } ?: "-"} edges=${field?.edgeCount ?: 0} elapsed=${elapsed}ms candidateMs=${candidateNanos / NANOS_PER_MILLISECOND} ready=${field != null} scanned=${builder?.geometricScanned} raycasts=${builder?.geometricRaycasts} hits=${builder?.geometricHits}" }
    }

    fun logCandidateSources(components: List<Pair<Set<Int>, RoomData>>, resolved: List<ResolvedRoomLandings>) {
        if (!PathPlanDiagnostics.enabled) return
        pathLog {
            "field candidates " + components.zip(resolved).joinToString {
                (room, landings) -> "${room.second.name}:${landings.positions.size}${if (landings.compiled) "/atlas" else "/runtime(${landings.failure})"}"
            }
        }
    }

    private fun describeRooms(field: EtherwarpHopField.BuiltField, handle: EtherwarpHopField.Handle): Map<String, Int> =
        field.blocks().groupingBy { block ->
            MapGrid.roomCellOf(block.x + BLOCK_CENTER, block.z + BLOCK_CENTER)
                ?.let { (handle.grid.getOrNull(it) as? MapTile.Room)?.data?.name }
                ?: "?"
        }.eachCount()

    private const val BLOCK_CENTER = 0.5
    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
