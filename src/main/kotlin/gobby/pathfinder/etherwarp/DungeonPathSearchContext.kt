package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.PreparedDungeonRouteCache
import gobby.pathfinder.navigation.DirectRouteCache
import gobby.pathfinder.navigation.DependencyValidatedRoute
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal class DungeonPathSearchContext(
    val from: Vec3,
    val goalBlock: BlockPos,
    val kind: EtherwarpKind,
    val config: EtherwarpPathConfig,
    val enterRoom: Boolean,
    val cache: BlockCache.SnapshotView,
    val deadline: SearchDeadline,
    val fallbackDeadline: SearchDeadline,
    val goalCell: Int?,
    val mapRevision: Long,
    val exactGoal: Boolean,
    val landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL
) {
    fun complete(path: List<EtherwarpNode>?): List<EtherwarpNode>? {
        if (landsOnBlacklistedTile(path)) return null
        val validated = path?.let { EtherwarpPathfinder.revalidateSnapshotWithDependencies(it, kind.searchRange(config), kind, cache) }
        if (validated == null) {
            pathLog { "route rejected by snapshot revalidation hops=${path?.let { it.size - 1 } ?: -1} nodes=${path?.map { PathPlanDiagnostics.describeBlock(it.pos) }}" }
            return null
        }
        return completeValidated(validated)
    }

    fun completeDirect(path: List<EtherwarpNode>?): List<EtherwarpNode>? {
        if (landsOnBlacklistedTile(path)) return null
        val validated = path?.let { EtherwarpRouteValidator.validateKnownDirect(it, cache) }
            ?: return null
        goalCell?.let { targetCell ->
            DirectRouteCache.put(validated, goalBlock, targetCell, enterRoom, kind, config, cache, mapRevision)
        }
        return validated.nodes
    }

    private fun completeValidated(validated: DependencyValidatedRoute): List<EtherwarpNode> {
        goalCell?.let { targetCell ->
            PreparedDungeonRouteCache.putValidated(
                validated,
                goalBlock,
                targetCell,
                enterRoom,
                kind,
                config,
                cache,
                mapRevision
            )
        }
        return validated.nodes
    }

    fun landsOnBlacklistedTile(path: List<EtherwarpNode>?): Boolean {
        val offender = path?.drop(FIRST_LANDING)?.firstOrNull { !landingFilter(it.pos) } ?: return false
        pathLog { "route rejected, lands on a blacklisted tile at ${PathPlanDiagnostics.describeBlock(offender.pos)}" }
        return true
    }

    fun fail(): List<EtherwarpNode>? {
        if (!deadline.expired && !Thread.currentThread().isInterrupted) {
            goalCell?.let { targetCell ->
                PreparedDungeonRouteCache.recordFailure(
                    from,
                    goalBlock,
                    targetCell,
                    enterRoom,
                    kind,
                    config,
                    cache,
                    mapRevision
                )
            }
        }
        return null
    }
    private companion object {
        const val FIRST_LANDING = 1
    }
}
