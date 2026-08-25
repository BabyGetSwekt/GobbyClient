package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.RoomStep
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal data class AtlasRoomPreparation(
    val rooms: List<PreparedGraphRoom>,
    val portals: List<PreparedPortal>
)

internal object AtlasRoomPreparationCache {
    private const val MAX_BYTES = 8L * 1024L * 1024L
    private val entries = WeightedPositiveCache<Key, Entry>(MAX_BYTES, Entry::estimatedBytes)

    @Synchronized
    fun get(
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView
    ): AtlasRoomPreparation? = entries.get(key(rooms, grid, snapshot))?.preparation

    @Synchronized
    fun put(
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        preparation: AtlasRoomPreparation
    ) {
        if (preparation.portals.size != rooms.lastIndex) return
        if (preparation.rooms.any { it.positions.isEmpty() && !it.runtimeBridge }) return
        entries.put(key(rooms, grid, snapshot), Entry(preparation))
    }

    @Synchronized
    fun clear() = entries.clear()

    private fun key(
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView
    ) = Key(snapshot.worldEpoch, snapshot.cacheVersion, grid.contentHashCode(), rooms.map(::roomKey))

    private fun roomKey(step: RoomStep) = RoomKey(step.cellIndex, step.data.name, step.data.shape)

    private data class Key(
        val worldEpoch: Long,
        val cacheVersion: Long,
        val gridHash: Int,
        val rooms: List<RoomKey>
    )

    private data class RoomKey(val cellIndex: Int, val name: String, val shape: String)

    private data class Entry(val preparation: AtlasRoomPreparation) {
        val estimatedBytes: Long
            get() = BASE_BYTES + preparation.rooms.sumOf(::roomBytes) + preparation.portals.sumOf(::portalBytes)
    }

    private fun roomBytes(room: PreparedGraphRoom): Long =
        (room.positions.size + room.anchors.size + room.runtimeSeeds.size + room.liveConnectors.size) * POSITION_BYTES +
            room.edges.size * EDGE_BYTES

    private fun portalBytes(portal: PreparedPortal): Long =
        BASE_PORTAL_BYTES + (portal.fromCandidates.size + portal.toCandidates.size) * POSITION_BYTES

    private const val BASE_BYTES = 128L
    private const val BASE_PORTAL_BYTES = 64L
    private const val POSITION_BYTES = 16L
    private const val EDGE_BYTES = 32L
}
