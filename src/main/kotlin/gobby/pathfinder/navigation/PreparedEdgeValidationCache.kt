package gobby.pathfinder.navigation

import gobby.pathfinder.etherwarp.Aim
import gobby.pathfinder.etherwarp.EtherwarpKind
import gobby.pathfinder.world.BlockCache

internal class PreparedEdgeValidationCache(
    private val range: Double,
    private val kind: EtherwarpKind,
    private val snapshot: BlockCache.SnapshotView
) {
    private val entries = HashMap<PreparedDirectedEdge, Aim?>()

    fun resolve(edge: PreparedDirectedEdge): Aim? {
        if (edge in entries) return entries[edge]
        return ValidatedEdgeCache.resolvePrepared(edge, range, kind, snapshot).also { entries[edge] = it }
    }
}
