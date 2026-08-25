package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.RoomStep
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos

internal object RoomPortalCandidateResolver {
    fun find(
        step: RoomStep,
        component: Set<Int>,
        seed: BlockPos,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        indexer: RoomLandingIndex
    ): List<BlockPos> {
        val atlasResult = CompiledRoomLandingAtlas.candidates(step.data, component, grid, snapshot)
        val runtime = if (atlasResult.compatible) indexer.query(listOf(seed))
        else indexer.queryRoom(component, grid, listOf(seed))
        val runtimeInside = inside(runtime, component, grid)
        val atlas = atlasResult.positions
            .sortedBy { VecUtils.distanceSq(it, seed) }
        return (runtimeInside + atlas).distinct().take(MAX_CANDIDATES)
    }

    private fun inside(candidates: List<BlockPos>, component: Set<Int>, grid: Array<MapTile>): List<BlockPos> =
        candidates.filter { DungeonRooms.containingRoomCell(grid, it.x + CENTER, it.z + CENTER) in component }

    private const val CENTER = 0.5
    private const val MAX_CANDIDATES = 64
}
