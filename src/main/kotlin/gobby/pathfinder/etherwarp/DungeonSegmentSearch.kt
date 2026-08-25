package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.search.SearchLane
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal object DungeonSegmentSearch {
    fun find(
        origin: Vec3,
        target: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        cache: BlockCache.SnapshotView,
        deadline: SearchDeadline,
        landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL
    ): List<EtherwarpNode>? {
        val outcome = EtherwarpPathfinder.searchOutcome(origin, target, kind, config, reached, cache.trackingMissingChunks(), deadline, SearchLane.HYBRID, landingFilter)
        pathLog { "    segment search ${PathPlanDiagnostics.describeOutcome(outcome)} ${PathPlanDiagnostics.describeBudget(deadline)}" }
        return outcome.path
    }
}
