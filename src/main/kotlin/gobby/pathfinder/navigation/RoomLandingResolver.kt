package gobby.pathfinder.navigation

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomData
import net.minecraft.core.BlockPos

internal data class ResolvedRoomLandings(
    val positions: List<BlockPos>,
    val anchors: List<BlockPos>,
    val edges: List<PreparedDirectedEdge>,
    val failure: AtlasMatchFailure?
) {
    val compiled: Boolean get() = failure == null
}

internal object RoomLandingResolver {

    fun resolve(
        data: RoomData,
        component: Set<Int>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        index: RoomLandingIndex,
        seeds: List<BlockPos>,
        runtimeLimit: Int = RUNTIME_LIMIT
    ): ResolvedRoomLandings {
        val compiled = CompiledRoomLandingAtlas.candidates(data, component, grid, snapshot)
        if (compiled.compatible) return ResolvedRoomLandings(compiled.positions, compiled.anchors, compiled.edges, null)
        val runtime = index.queryRoomSpread(component, grid, seeds, limit = runtimeLimit)
        return ResolvedRoomLandings(runtime, runtime.take(RUNTIME_ANCHORS), emptyList(), compiled.failure ?: AtlasMatchFailure.NO_LIVE_LANDINGS)
    }

    private const val RUNTIME_LIMIT = 512
    private const val RUNTIME_ANCHORS = 8
}
