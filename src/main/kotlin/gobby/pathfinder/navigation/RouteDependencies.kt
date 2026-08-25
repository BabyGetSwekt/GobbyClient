package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.EtherwarpNode
import gobby.pathfinder.etherwarp.EtherwarpPathConfig
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private const val DEPENDENCY_BYTES = 128L
private const val CHUNK_BYTES = 32L
private const val OVERRIDE_BYTES = 16L
private const val ROUTE_BYTES = 64L
private const val NODE_BYTES = 80L

data class LandingKey(val supportBlock: Long, val standingOffsetSixteenths: Int) {
    companion object {
        fun from(node: EtherwarpNode) = LandingKey(node.pos.asLong(), ((node.y - node.pos.y) * 16.0).roundToInt())

        fun from(position: Vec3): LandingKey {
            val support = BlockPos.containing(position)
            return LandingKey(support.asLong(), ((position.y - support.y) * 16.0).roundToInt())
        }
    }
}

internal fun routeConfigKey(config: EtherwarpPathConfig): Long =
    java.lang.Double.doubleToRawLongBits(config.etherRange) xor
        java.lang.Long.rotateLeft(java.lang.Double.doubleToRawLongBits(config.hWeight), H_WEIGHT_ROTATION) xor
        (config.yawStep.toRawBits().toLong() shl 32) xor
        (config.pitchStep.toRawBits().toLong() and UINT_MASK)

private const val UINT_MASK = 0xffffffffL
private const val H_WEIGHT_ROTATION = 17

data class ChunkDependency(val key: Long, val revision: Long, val collisionFingerprint: Long)
data class BlockDependency(val position: Long, val stateId: Int)

data class EdgeDependency(
    val from: LandingKey,
    val to: LandingKey,
    val traversedChunks: List<ChunkDependency>,
    val targetSupport: BlockDependency,
    val feetClearance: BlockDependency,
    val headClearance: BlockDependency,
    val relevantPassableOverrides: Set<Long>
) {
    val estimatedBytes: Long get() = DEPENDENCY_BYTES + traversedChunks.size * CHUNK_BYTES + relevantPassableOverrides.size * OVERRIDE_BYTES

    fun check(snapshot: BlockCache.SnapshotView): DependencyCheck {
        val missing = traversedChunks.asSequence().map { it.key }.filter { snapshot.chunkRevision(it) == null }.toSet()
        if (missing.isNotEmpty()) return DependencyCheck.Missing(missing)
        val keys = traversedChunks.mapTo(HashSet()) { it.key }
        return if (chunksChanged(snapshot) || blocksChanged(snapshot) || overridesChanged(snapshot, keys)) {
            DependencyCheck.Changed
        } else {
            DependencyCheck.Unchanged
        }
    }

    private fun chunksChanged(snapshot: BlockCache.SnapshotView): Boolean = traversedChunks.any {
        it.revision != snapshot.chunkRevision(it.key) || it.collisionFingerprint != snapshot.chunkCollisionFingerprint(it.key)
    }

    private fun blocksChanged(snapshot: BlockCache.SnapshotView): Boolean =
        targetSupport.changed(snapshot) || feetClearance.changed(snapshot) || headClearance.changed(snapshot)

    private fun overridesChanged(snapshot: BlockCache.SnapshotView, keys: Set<Long>): Boolean =
        snapshot.passableOverridesInChunks(keys) != relevantPassableOverrides

    companion object {
        fun capture(from: EtherwarpNode, to: EtherwarpNode, tracked: BlockCache.SnapshotView): EdgeDependency? {
            val supportState = tracked.getBlockState(to.pos) ?: return null
            val collisionTop = supportState.getCollisionShape(EmptyBlockGetter.INSTANCE, to.pos).takeUnless { it.isEmpty }?.max(Direction.Axis.Y) ?: return null
            val clearance = clearance(to.pos, collisionTop, tracked) ?: return null
            val chunks = chunkDependencies(tracked) ?: return null
            return EdgeDependency(
                LandingKey.from(from), LandingKey.from(to), chunks,
                BlockDependency(to.pos.asLong(), Block.getId(supportState)),
                clearance.feet,
                clearance.head,
                tracked.passableOverridesInChunks(chunks.mapTo(HashSet(), ChunkDependency::key))
            )
        }

        private fun clearance(pos: BlockPos, collisionTop: Double, tracked: BlockCache.SnapshotView): Clearance? {
            val feetPos = BlockPos(pos.x, pos.y + max(1, ceil(collisionTop).toInt()), pos.z)
            val feetState = tracked.getBlockState(feetPos) ?: return null
            val headState = tracked.getBlockState(feetPos.above()) ?: return null
            return Clearance(
                BlockDependency(feetPos.asLong(), Block.getId(feetState)),
                BlockDependency(feetPos.above().asLong(), Block.getId(headState))
            )
        }

        private fun chunkDependencies(tracked: BlockCache.SnapshotView): List<ChunkDependency>? {
            if (tracked.missingChunksAccessed().isNotEmpty()) return null
            return tracked.chunksAccessed().toSortedSet().map { key ->
                ChunkDependency(key, tracked.chunkRevision(key) ?: return null, tracked.chunkCollisionFingerprint(key) ?: return null)
            }
        }

        private data class Clearance(val feet: BlockDependency, val head: BlockDependency)
    }

}

sealed interface DependencyCheck {
    data object Unchanged : DependencyCheck
    data object Changed : DependencyCheck
    data class Missing(val keys: Set<Long>) : DependencyCheck
}

data class DependencyValidatedRoute(
    val nodes: List<EtherwarpNode>,
    val edges: List<EdgeDependency>,
    val completenessCertified: Boolean = false,
    val compatiblePreparedRoute: Boolean = false
) {
    val estimatedBytes: Long get() = ROUTE_BYTES + nodes.size * NODE_BYTES + edges.sumOf(EdgeDependency::estimatedBytes)
}

sealed interface CachedRouteValidation {
    data class Valid(val route: DependencyValidatedRoute, val refreshedEdges: Int) : CachedRouteValidation
    data class MissingChunks(val keys: Set<Long>) : CachedRouteValidation
    data class Invalid(val edgeIndex: Int) : CachedRouteValidation
}

private fun BlockDependency.changed(snapshot: BlockCache.SnapshotView): Boolean =
    Block.getId(snapshot.getBlockState(BlockPos.of(position)) ?: return true) != stateId
