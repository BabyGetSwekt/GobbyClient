package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.etherwarp.EtherwarpRouteValidator
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object DirectRouteCache {
    private val entries = WeightedPositiveCache<Key, Entry>(MAX_BYTES, Entry::estimatedBytes)

    fun preload() {
        entries.bytes
    }

    fun take(
        from: Vec3,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ): List<EtherwarpNode>? {
        val key = key(from, goal, targetCell, enterRoom, kind, config, snapshot, mapRevision)
        val cached = synchronized(this) { entries.get(key) } ?: return null
        if (cached.validatedVersion == snapshot.cacheVersion) return cached.nodes
        return when (val result = EtherwarpRouteValidator.validateCached(cached.nodes, cached.edges, kind.searchRange(config), kind, snapshot)) {
            is CachedRouteValidation.Valid -> {
                val refreshed = Entry(result.route.nodes, result.route.edges, snapshot.cacheVersion)
                synchronized(this) { entries.replaceIfCurrent(key, cached, refreshed) }
                result.route.nodes
            }
            is CachedRouteValidation.MissingChunks, is CachedRouteValidation.Invalid -> {
                synchronized(this) { entries.removeIfCurrent(key, cached) }
                null
            }
        }
    }

    fun put(
        route: DependencyValidatedRoute,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ) {
        if (route.nodes.size != DIRECT_ROUTE_SIZE || route.edges.size != DIRECT_EDGE_COUNT) return
        val key = key(route.nodes.first(), goal, targetCell, enterRoom, kind, config, snapshot, mapRevision)
        val entry = Entry(
            route.nodes,
            route.edges,
            snapshot.cacheVersion
        )
        synchronized(this) { entries.put(key, entry) }
    }

    fun clear() {
        synchronized(this) { entries.clear() }
    }



    private fun key(
        from: Vec3,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ) = Key(LandingKey.from(from), from.x, from.y, from.z, snapshot.worldEpoch, goal.asLong(), targetCell, enterRoom, kind.ordinal, routeConfigKey(config), mapRevision)

    private fun key(
        node: EtherwarpNode,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ) = Key(LandingKey.from(node), node.x, node.y, node.z, snapshot.worldEpoch, goal.asLong(), targetCell, enterRoom, kind.ordinal, routeConfigKey(config), mapRevision)

    private data class Entry(val nodes: List<EtherwarpNode>, val edges: List<EdgeDependency>, val validatedVersion: Long) {
        val estimatedBytes: Long get() = BASE_BYTES + nodes.size * NODE_BYTES + edges.sumOf(EdgeDependency::estimatedBytes)
    }

    private data class Key(
        val origin: LandingKey,
        val originX: Double,
        val originY: Double,
        val originZ: Double,
        val worldEpoch: Long,
        val goal: Long,
        val targetCell: Int,
        val enterRoom: Boolean,
        val kind: Int,
        val config: Long,
        val mapRevision: Long
    )

    private const val MAX_BYTES = 4L * 1024L * 1024L
    private const val BASE_BYTES = 96L
    private const val NODE_BYTES = 80L

    private const val DIRECT_ROUTE_SIZE = 2
    private const val DIRECT_EDGE_COUNT = 1
}
