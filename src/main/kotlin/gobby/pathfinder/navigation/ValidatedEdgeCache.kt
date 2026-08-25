package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpRouteValidator
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicLong

internal object ValidatedEdgeCache {
    private const val MAX_BYTES = 16L * 1024L * 1024L
    private const val MAX_PREPARED_BYTES = 16L * 1024L * 1024L
    private const val NODE_BYTES = 96L
    private val entries = WeightedPositiveCache<Key, Entry>(MAX_BYTES) { NODE_BYTES + it.dependency.estimatedBytes }
    private val preparedEntries = WeightedPositiveCache<PreparedKey, Entry>(MAX_PREPARED_BYTES) { NODE_BYTES + it.dependency.estimatedBytes }
    private val clearGeneration = AtomicLong()

    fun resolve(from: BlockPos, to: BlockPos, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): Aim? {
        if (snapshot.isDirectSnapshot) return kind.aimAt(eye(from, kind), to, range, snapshot = snapshot)
        val key = Key(snapshot.worldEpoch, from.asLong(), to.asLong(), range, kind)
        val generation = clearGeneration.get()
        val cached = synchronized(this) { entries.get(key) }
        cached?.let { entry ->
            if (entry.validatedVersion == snapshot.cacheVersion) return entry.aim
            when (entry.dependency.check(snapshot)) {
                DependencyCheck.Unchanged -> return entry.aim
                is DependencyCheck.Missing -> synchronized(this) {
                    if (generation == clearGeneration.get()) entries.remove(key)
                }
                DependencyCheck.Changed -> return validateAndStore(key, from, to, range, kind, snapshot, generation)
            }
        }
        return validateAndStore(key, from, to, range, kind, snapshot, generation)
    }

    fun resolvePrepared(edge: PreparedDirectedEdge, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): Aim? {
        if (snapshot.isDirectSnapshot) return validatePreparedAim(edge, range, kind, snapshot)
        val key = PreparedKey(snapshot.worldEpoch, edge.from.asLong(), edge.to.asLong(), range, kind, edge.yaw, edge.pitch)
        val cached = synchronized(this) { preparedEntries.get(key) }
        cached?.let { entry ->
            if (entry.validatedVersion == snapshot.cacheVersion) return entry.aim
            when (entry.dependency.check(snapshot)) {
                DependencyCheck.Unchanged -> return entry.aim
                is DependencyCheck.Missing -> synchronized(this) { preparedEntries.remove(key) }
                DependencyCheck.Changed -> return validatePreparedAndStore(key, edge, range, kind, snapshot)
            }
        }
        return validatePreparedAndStore(key, edge, range, kind, snapshot)
    }

    private fun validatePreparedAim(edge: PreparedDirectedEdge, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): Aim? {
        val access = EtherwarpUtils.cachedAccess(snapshot) ?: return null
        val eye = Vec3(edge.from.x + CENTER, kind.landingY(edge.from.y) + kind.eyeHeight(), edge.from.z + CENTER)
        val aim = Aim(edge.yaw, edge.pitch)
        return aim.takeIf { EtherwarpUtils.validateAim(eye, edge.to, range, it.yaw to it.pitch, access) != EtherwarpUtils.EtherPos.NONE }
    }

    @Synchronized
    fun clear() {
        clearGeneration.incrementAndGet()
        entries.clear()
        preparedEntries.clear()
    }

    private fun validatePreparedAndStore(key: PreparedKey, edge: PreparedDirectedEdge, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): Aim? {
        val source = node(edge.from, kind).also {
            it.yaw = edge.yaw
            it.pitch = edge.pitch
        }
        val route = EtherwarpRouteValidator.validate(listOf(source, node(edge.to, kind)), range, kind, snapshot) ?: return null
        val validated = route.nodes.firstOrNull()?.let { Aim(it.yaw, it.pitch) } ?: return null
        if (!sameAim(validated, Aim(edge.yaw, edge.pitch))) return null
        val dependency = route.edges.singleOrNull() ?: return null
        val entry = Entry(validated, dependency, snapshot.cacheVersion)
        synchronized(this) { preparedEntries.put(key, entry) }
        return validated
    }

    private fun validateAndStore(key: Key, from: BlockPos, to: BlockPos, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView, generation: Long): Aim? {
        val route = EtherwarpRouteValidator.validate(listOf(node(from, kind), node(to, kind)), range, kind, snapshot) ?: return null
        if (route.edges.size != 1) return null
        val value = Entry(Aim(route.nodes.first().yaw, route.nodes.first().pitch), route.edges.first(), snapshot.cacheVersion)
        synchronized(this) {
            if (generation == clearGeneration.get()) entries.put(key, value)
        }
        return value.aim
    }

    private fun node(pos: BlockPos, kind: EtherwarpKind) = EtherwarpNode(pos.x + CENTER, kind.landingY(pos.y), pos.z + CENTER, pos, ZERO_SCORE, ZERO_SCORE, null, ZERO_AIM, ZERO_AIM)

    private fun eye(pos: BlockPos, kind: EtherwarpKind) = net.minecraft.world.phys.Vec3(pos.x + CENTER, kind.landingY(pos.y) + kind.eyeHeight(), pos.z + CENTER)
    private data class Key(val epoch: Long, val from: Long, val to: Long, val range: Double, val kind: EtherwarpKind)
    private data class PreparedKey(val epoch: Long, val from: Long, val to: Long, val range: Double, val kind: EtherwarpKind, val yaw: Float, val pitch: Float)
    private data class Entry(val aim: Aim, val dependency: EdgeDependency, val validatedVersion: Long)
    private const val CENTER = 0.5
    private const val ZERO_SCORE = 0.0
    private const val ZERO_AIM = 0f

    private fun sameAim(first: Aim, second: Aim): Boolean =
        first.yaw.toRawBits() == second.yaw.toRawBits() && first.pitch.toRawBits() == second.pitch.toRawBits()
}
