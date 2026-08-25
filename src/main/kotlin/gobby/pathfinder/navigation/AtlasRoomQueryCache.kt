package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.etherwarp.EtherwarpRouteValidator
import gobby.pathfinder.etherwarp.RoomStep
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object AtlasRoomQueryCache {
    private const val MAX_BYTES = 16L * 1024L * 1024L
    private val entries = WeightedPositiveCache<Key, Entry>(MAX_BYTES) { entry ->
        BASE_BYTES + entry.route.nodes.size * NODE_BYTES + entry.route.edges.sumOf(EdgeDependency::estimatedBytes)
    }

    fun get(
        start: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView
    ): DependencyValidatedRoute? {
        val cacheKey = key(start, goal, kind, config, rooms, grid, snapshot)
        val entry = synchronized(this) { entries.get(cacheKey) } ?: return null
        if (entry.validatedVersion == snapshot.cacheVersion) return entry.route
        return when (val validation = EtherwarpRouteValidator.validateCached(
            entry.route.nodes, entry.route.edges, kind.searchRange(config), kind, snapshot
        )) {
            is CachedRouteValidation.Valid -> entry.route.copy(
                nodes = validation.route.nodes,
                edges = validation.route.edges
            ).also { refreshed -> synchronized(this) { entries.replaceIfCurrent(cacheKey, entry, Entry(refreshed, snapshot.cacheVersion)) } }
            is CachedRouteValidation.MissingChunks -> null
            is CachedRouteValidation.Invalid -> null.also { synchronized(this) { entries.removeIfCurrent(cacheKey, entry) } }
        }
    }

    fun put(
        start: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView,
        route: DependencyValidatedRoute
    ) {
        val entry = Entry(route, snapshot.cacheVersion)
        synchronized(this) { entries.put(key(start, goal, kind, config, rooms, grid, snapshot), entry) }
    }

    fun clear() = synchronized(this) { entries.clear() }



    private fun key(
        start: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        rooms: List<RoomStep>,
        grid: Array<MapTile>,
        snapshot: BlockCache.SnapshotView
    ) = Key(
        snapshot.worldEpoch,
        start.x.toRawBits(),
        start.y.toRawBits(),
        start.z.toRawBits(),
        goal.asLong(),
        kind.ordinal,
        routeConfigKey(config),
        grid.contentHashCode(),
        rooms.map { it.cellIndex }
    )

    private data class Key(
        val worldEpoch: Long,
        val startX: Long,
        val startY: Long,
        val startZ: Long,
        val goal: Long,
        val kind: Int,
        val config: Long,
        val gridHash: Int,
        val roomCells: List<Int>
    )

    private data class Entry(
        val route: DependencyValidatedRoute,
        val validatedVersion: Long
    )

    private const val BASE_BYTES = 64L
    private const val NODE_BYTES = 80L
}
