package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.etherwarp.SearchDeadline
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object AtlasExpandedProposal {
    fun find(
        start: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: gobby.pathfinder.world.BlockCache.SnapshotView,
        deadline: SearchDeadline,
        rooms: List<PreparedGraphRoom>,
        portals: List<PreparedPortal>,
        index: RoomLandingIndex,
        grid: Array<MapTile>
    ): GraphProposal {
        val expandedPortals = portals.map { portal -> portal.copy(
            fromCandidates = index.queryExpanded(listOf(portal.fromSeed), portal.fromCandidates),
            toCandidates = index.queryExpanded(listOf(portal.toSeed), portal.toCandidates)
        ) }
        val liveByRoom = LinkedHashMap<Int, LinkedHashSet<BlockPos>>()

        fun addLive(canonical: Int, values: List<BlockPos>) = liveByRoom.getOrPut(canonical, ::LinkedHashSet).addAll(values)
        rooms.firstOrNull()?.let { addLive(it.canonical, index.queryExpanded(listOf(BlockPos.containing(start).below()))) }
        rooms.lastOrNull()?.let { addLive(it.canonical, index.queryExpanded(listOf(goal))) }
        expandedPortals.forEach { portal ->
            addLive(portal.fromCanonical, portal.fromCandidates)
            addLive(portal.toCanonical, portal.toCandidates)
        }
        val expandedRooms = rooms.map { room -> room.copy(liveConnectors = (room.liveConnectors + liveByRoom[room.canonical].orEmpty()).distinct()) }
        return RoomReachabilityGraph(start, goal, kind.searchRange(config), kind, snapshot, System.nanoTime() + deadline.remainingNanos, expandedRooms, expandedPortals).search()
    }
}
