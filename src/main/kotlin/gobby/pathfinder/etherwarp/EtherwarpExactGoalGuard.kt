package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.PreparedDungeonRouteCache
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal fun recordUnavailableExactGoal(
    from: Vec3,
    goal: BlockPos,
    kind: EtherwarpKind,
    config: EtherwarpPathConfig,
    cache: BlockCache.SnapshotView,
    targetCell: Int,
    mapRevision: Long
): Boolean {
    if (kind != EtherwarpKind.ETHERWARP) return false
    val target = kind.goal(goal)
    if (EtherwarpUtils.isEtherwarpable(target, cached = true, snapshot = cache) || cache.missingChunksAccessed().isNotEmpty()) return false
    PreparedDungeonRouteCache.recordFailure(from, goal, targetCell, false, kind, config, cache, mapRevision)
    return true
}
