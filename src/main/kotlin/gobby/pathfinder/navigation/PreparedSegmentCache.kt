package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.etherwarp.EtherwarpRouteValidator
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import java.util.concurrent.atomic.AtomicLong

internal object PreparedSegmentCache {
    private const val MAX_BYTES = 16L * 1024L * 1024L
    private const val BASE_BYTES = 64L
    private const val NODE_BYTES = 80L
    private const val MAX_FAILURES = 4096
    private val entries = WeightedPositiveCache<Key, Entry>(MAX_BYTES, Entry::estimatedBytes)
    private val failures = WeightedPositiveCache<FailureKey, Boolean>(MAX_FAILURES.toLong()) { 1L }
    private val clearGeneration = AtomicLong()

    fun take(key: Key, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): List<EtherwarpNode>? {
        val generation = clearGeneration.get()
        val entry = synchronized(this) { entries.get(key) } ?: return null
        if (entry.validatedVersion == snapshot.cacheVersion) return entry.nodes
        return when (val result = EtherwarpRouteValidator.validateCached(entry.nodes, entry.dependencies, range, kind, snapshot)) {
            is CachedRouteValidation.Valid -> {
                synchronized(this) {
                    if (generation == clearGeneration.get()) entries.put(key, Entry(result.route.nodes, result.route.edges, snapshot.cacheVersion))
                }
                result.route.nodes
            }
            is CachedRouteValidation.MissingChunks, is CachedRouteValidation.Invalid -> {
                synchronized(this) {
                    if (generation == clearGeneration.get()) entries.remove(key)
                }
                null
            }
        }
    }

    fun put(key: Key, route: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView) {
        val generation = clearGeneration.get()
        val validated = EtherwarpRouteValidator.validate(route, range, kind, snapshot) ?: return
        synchronized(this) {
            if (generation == clearGeneration.get()) entries.put(key, Entry(validated.nodes, validated.edges, snapshot.cacheVersion))
        }
    }

    @Synchronized
    fun clear() {
        clearGeneration.incrementAndGet()
        entries.clear()
        failures.clear()
    }

    fun takeOrCompute(
        origin: LandingKey?,
        target: BlockPos,
        enterRoom: Boolean,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        mapRevision: Long,
        snapshot: BlockCache.SnapshotView,
        cacheFailure: () -> Boolean = { true },
        compute: () -> List<EtherwarpNode>?
    ): List<EtherwarpNode>? {
        if (origin == null) return compute()
        val key = Key(snapshot.worldEpoch, mapRevision, origin, target.asLong(), enterRoom, kind.ordinal, routeConfigKey(config))
        take(key, kind.searchRange(config), kind, snapshot)?.let { return it }
        val failedKey = failureKey(snapshot, key)
        if (hasFailure(failedKey)) return null
        val result = compute()
        if (result == null) {
            if (cacheFailure()) recordFailure(failedKey)
            return null
        }
        clearFailure(failedKey)
        put(key, result, kind.searchRange(config), kind, snapshot)
        return result
    }

    data class Key(
        val worldEpoch: Long,
        val mapRevision: Long,
        val origin: LandingKey,
        val goal: Long,
        val enterRoom: Boolean,
        val kind: Int,
        val config: Long
    )

    private data class Entry(val nodes: List<EtherwarpNode>, val dependencies: List<EdgeDependency>, val validatedVersion: Long) {
        val estimatedBytes: Long get() = BASE_BYTES + nodes.size * NODE_BYTES + dependencies.sumOf(EdgeDependency::estimatedBytes)
    }

    private fun failureKey(snapshot: BlockCache.SnapshotView, key: Key) = FailureKey(snapshot.cacheVersion, key)

    @Synchronized
    private fun hasFailure(key: FailureKey): Boolean = failures.get(key) != null

    @Synchronized
    private fun recordFailure(key: FailureKey) = failures.put(key, true)

    @Synchronized
    private fun clearFailure(key: FailureKey) = failures.remove(key)

    private data class FailureKey(val cacheVersion: Long, val key: Key)
}
