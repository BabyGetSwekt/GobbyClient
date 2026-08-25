package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.etherwarp.EtherwarpRouteValidator
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import gobby.pathfinder.navigation.CachedRouteValidation

object PreparedDungeonRouteCache {
    private const val MAX_BYTES = 32L * 1024L * 1024L
    private const val FIRST_NODE_INDEX = 1
    private const val MAX_FAILURES = 4096L
    private const val ZERO_SCORE = 0.0
    private val entries = WeightedPositiveCache<Key, Entry>(MAX_BYTES, Entry::estimatedBytes)
    private val failures = WeightedPositiveCache<FailureKey, Boolean>(MAX_FAILURES) { 1L }

    internal fun preload() {
        entries.bytes
        failures.bytes
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
        val key = key(goal, targetCell, enterRoom, kind, config, snapshot)
        val origin = LandingKey.from(from)
        val failureKey = FailureKey(snapshot.cacheVersion, key, origin, mapRevision)
        val entry = synchronized(this) {
            if (failures.get(failureKey) != null) return@synchronized null
            entries.get(key)
        } ?: return null
        if (entry.goal != goal) {
            synchronized(this) { entries.removeIfCurrent(key, entry) }
            return null
        }
        val validated = if (entry.nodes.size < 2 || entry.validatedVersion == snapshot.cacheVersion) {
            entry.nodes
        } else when (val result = EtherwarpRouteValidator.validateCached(entry.nodes, entry.dependencies, kind.searchRange(config), kind, snapshot)) {
            is CachedRouteValidation.Valid -> {
                val refreshed = entry.copy(nodes = result.route.nodes, dependencies = result.route.edges, validatedVersion = snapshot.cacheVersion)
                synchronized(this) { entries.replaceIfCurrent(key, entry, refreshed) }
                result.route.nodes
            }
            is CachedRouteValidation.MissingChunks, is CachedRouteValidation.Invalid -> {
                synchronized(this) { entries.removeIfCurrent(key, entry) }
                return null
            }
        }
        return rewriteFirstHop(from, validated, kind, config, snapshot)
    }

    fun recordFailure(
        from: Vec3,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ) {
        if (snapshot.missingChunksAccessed().isNotEmpty()) return
        synchronized(this) {
            failures.put(failureKey(from, goal, targetCell, enterRoom, kind, config, snapshot, mapRevision), true)
        }
    }

    fun hasFailure(
        from: Vec3,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ): Boolean = synchronized(this) {
        failures.get(failureKey(from, goal, targetCell, enterRoom, kind, config, snapshot, mapRevision)) != null
    }

    fun put(
        route: List<EtherwarpNode>,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ) {
        if (route.size < 2) return
        val key = key(goal, targetCell, enterRoom, kind, config, snapshot)
        val validated = EtherwarpRouteValidator.validate(route, kind.searchRange(config), kind, snapshot) ?: return
        val entry = entry(validated, goal, snapshot.cacheVersion)
        val origin = LandingKey.from(validated.nodes.first())
        synchronized(this) { putValidated(key, entry, origin, snapshot.cacheVersion, mapRevision) }
    }

    fun putValidated(
        route: DependencyValidatedRoute,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ) {
        val key = key(goal, targetCell, enterRoom, kind, config, snapshot)
        val entry = entry(route, goal, snapshot.cacheVersion)
        val origin = LandingKey.from(route.nodes.first())
        synchronized(this) { putValidated(key, entry, origin, snapshot.cacheVersion, mapRevision) }
    }

    fun clear() {
        synchronized(this) {
            entries.clear()
            failures.clear()
        }
        DirectRouteCache.clear()
    }

    private fun putValidated(key: Key, entry: Entry, origin: LandingKey, cacheVersion: Long, mapRevision: Long) {
        failures.remove(FailureKey(cacheVersion, key, origin, mapRevision))
        entries.put(key, entry)
    }

    private fun entry(route: DependencyValidatedRoute, goal: BlockPos, cacheVersion: Long) = Entry(route.nodes.drop(FIRST_NODE_INDEX).map(::copyDetached), route.edges.drop(FIRST_NODE_INDEX), goal, cacheVersion)



    private fun failureKey(
        from: Vec3,
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView,
        mapRevision: Long
    ): FailureKey = FailureKey(
        snapshot.cacheVersion,
        key(goal, targetCell, enterRoom, kind, config, snapshot),
        LandingKey.from(from),
        mapRevision
    )

    private fun rewriteFirstHop(
        from: Vec3,
        tail: List<EtherwarpNode>,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView
    ): List<EtherwarpNode>? {
        if (tail.isEmpty()) return null
        if (tail.size >= 2 && samePosition(from, tail.first())) return tail.map(::copyDetached)
        val range = kind.searchRange(config)
        val eye = Vec3(from.x, from.y + kind.eyeHeight(), from.z)
        for (index in tail.lastIndex downTo 0) {
            val aim = kind.aimAt(eye, tail[index].pos, range, snapshot = snapshot) ?: continue
            val start = EtherwarpNode(
                from.x,
                from.y,
                from.z,
                BlockPos.containing(from),
                ZERO_SCORE,
                ZERO_SCORE,
                null,
                aim.yaw,
                aim.pitch
            )
            return buildList(tail.size - index + FIRST_NODE_INDEX) {
                add(start)
                addAll(tail.subList(index, tail.size).map(::copyDetached))
            }
        }
        return null
    }

    private fun samePosition(from: Vec3, node: EtherwarpNode): Boolean =
        abs(from.x - node.x) <= POSITION_EPSILON &&
            abs(from.y - node.y) <= POSITION_EPSILON &&
            abs(from.z - node.z) <= POSITION_EPSILON

    private fun key(
        goal: BlockPos,
        targetCell: Int,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        snapshot: BlockCache.SnapshotView
    ) = Key(
        snapshot.worldEpoch,
        goal.asLong(),
        targetCell,
        enterRoom,
        kind.ordinal,
        routeConfigKey(config)
    )

    private fun copyDetached(node: EtherwarpNode) = EtherwarpNode(
        node.x, node.y, node.z, node.pos, node.g, node.h, null, node.yaw, node.pitch
    )

    private data class Key(
        val worldEpoch: Long,
        val goal: Long,
        val targetCell: Int,
        val enterRoom: Boolean,
        val kind: Int,
        val config: Long
    )

    private data class Entry(
        val nodes: List<EtherwarpNode>,
        val dependencies: List<EdgeDependency>,
        val goal: BlockPos,
        val validatedVersion: Long
    ) {
        val estimatedBytes: Long get() = BASE_BYTES + nodes.size * NODE_BYTES + dependencies.sumOf(EdgeDependency::estimatedBytes)
    }

    private data class FailureKey(val cacheVersion: Long, val key: Key, val origin: LandingKey, val mapRevision: Long)

    private const val BASE_BYTES = 96L
    private const val NODE_BYTES = 80L
    private const val POSITION_EPSILON = 1.0E-6
    private const val ZERO_AIM = 0f
}
