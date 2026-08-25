package gobby.pathfinder.etherwarp

import gobby.pathfinder.navigation.CachedRouteValidation
import gobby.pathfinder.navigation.DependencyCheck
import gobby.pathfinder.navigation.DependencyValidatedRoute
import gobby.pathfinder.navigation.EdgeDependency
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.world.phys.Vec3
import java.util.ArrayList

object EtherwarpRouteValidator {
    fun validateKnownDirect(path: List<EtherwarpNode>, snapshot: BlockCache.SnapshotView): DependencyValidatedRoute? {
        if (path.size != 2) return null
        val nodes = path.map(::copyDetached).toMutableList()
        val tracked = snapshot.isolatedTrackingReads()
        val dependency = EdgeDependency.capture(nodes.first(), nodes.last(), tracked)
        snapshot.mergeAccessedReads(tracked)
        if (dependency == null || tracked.missingChunksAccessed().isNotEmpty()) return null
        nodes.last().yaw = 0f
        nodes.last().pitch = 0f
        return DependencyValidatedRoute(nodes, listOf(dependency))
    }

    fun validate(path: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): DependencyValidatedRoute? {
        if (path.size < 2) return DependencyValidatedRoute(path.map(::copyDetached), emptyList())
        val nodes = path.map(::copyDetached).toMutableList()
        val edges = ArrayList<EdgeDependency>(nodes.size - 1)
        for (index in 0 until nodes.lastIndex) {
            when (val result = validateEdge(nodes[index], nodes[index + 1], range, kind, snapshot)) {
                is EdgeValidation.Valid -> {
                    nodes[index].yaw = result.node.yaw
                    nodes[index].pitch = result.node.pitch
                    edges += result.dependency
                }
                is EdgeValidation.Missing, EdgeValidation.Invalid -> {
                    if (nodes[index].pos != nodes[index + 1].pos && PathPlanDiagnostics.enabled && PathPlanDiagnostics.withinSampleQuota(REJECTED_EDGE_SAMPLE)) pathLog { describeRejectedEdge(index, nodes[index], nodes[index + 1], range, kind, result, snapshot) }
                    return null
                }
            }
        }
        nodes.last().yaw = 0f
        nodes.last().pitch = 0f
        return DependencyValidatedRoute(nodes, edges)
    }

    fun validateCached(path: List<EtherwarpNode>, dependencies: List<EdgeDependency>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): CachedRouteValidation {
        if (path.size < 2 || dependencies.size != path.size - 1) return CachedRouteValidation.Invalid(0)
        val nodes = path.map(::copyDetached).toMutableList()
        val refreshedEdges = ArrayList<EdgeDependency>(dependencies.size)
        var refreshed = 0
        for (index in dependencies.indices) {
            when (val check = dependencies[index].check(snapshot)) {
                DependencyCheck.Unchanged -> refreshedEdges += dependencies[index]
                is DependencyCheck.Missing -> return CachedRouteValidation.MissingChunks(check.keys)
                DependencyCheck.Changed -> when (val result = validateEdge(nodes[index], nodes[index + 1], range, kind, snapshot)) {
                    is EdgeValidation.Valid -> {
                        nodes[index].yaw = result.node.yaw
                        nodes[index].pitch = result.node.pitch
                        refreshedEdges += result.dependency
                        refreshed++
                    }
                    is EdgeValidation.Missing -> return CachedRouteValidation.MissingChunks(result.keys)
                    EdgeValidation.Invalid -> return CachedRouteValidation.Invalid(index)
                }
            }
        }
        nodes.last().yaw = 0f
        nodes.last().pitch = 0f
        return CachedRouteValidation.Valid(DependencyValidatedRoute(nodes, refreshedEdges), refreshed)
    }

    private fun validateEdge(from: EtherwarpNode, to: EtherwarpNode, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView,): EdgeValidation {
        val tracked = snapshot.isolatedTrackingReads()
        val source = copyDetached(from)
        val target = copyDetached(to)
        val eye = Vec3(source.x, source.y + kind.eyeHeight(), source.z)
        val storedAim = Aim(source.yaw, source.pitch)
        val access = EtherwarpUtils.cachedAccess(tracked) ?: return EdgeValidation.Invalid
        val storedValid = EtherwarpUtils.validateAim(eye, target.pos, range, storedAim.yaw to storedAim.pitch, access) != EtherwarpUtils.EtherPos.NONE
        val aim = if (storedValid) storedAim else kind.aimAt(eye, target.pos, range, snapshot = tracked)
        if (aim == null || (!storedValid && !isAimValid(eye, target, range, aim, access))) return invalidEdge(snapshot, tracked)
        source.yaw = aim.yaw
        source.pitch = aim.pitch
        target.yaw = 0f
        target.pitch = 0f
        val dependency = EdgeDependency.capture(source, target, tracked)
        snapshot.mergeAccessedReads(tracked)
        return dependency?.let { EdgeValidation.Valid(source, it) } ?: invalidEdge(snapshot, tracked)
    }

    private fun describeRejectedEdge(
        index: Int,
        from: EtherwarpNode,
        to: EtherwarpNode,
        range: Double,
        kind: EtherwarpKind,
        result: EdgeValidation,
        snapshot: BlockCache.SnapshotView
    ): String {
        val reason = if (result is EdgeValidation.Missing) "missingChunks(${result.keys.size})" else "aimInvalid"
        val eye = Vec3(from.x, from.y + kind.eyeHeight(), from.z)
        val access = EtherwarpUtils.cachedAccess(snapshot)
        val storedHit = access?.let {
            EtherwarpUtils.etherwarpRaycast(eye, EtherwarpUtils.directionFromAngles(from.yaw, from.pitch).scale(range), it)
        }
        val recomputed = access?.let { EtherwarpUtils.quickAim(to.pos, eye, range, it) }
        return "edge $index rejected ${PathPlanDiagnostics.describeBlock(from.pos)} -> ${PathPlanDiagnostics.describeBlock(to.pos)}" +
            " reason=$reason stand=${PathPlanDiagnostics.describeVec(Vec3(from.x, from.y, from.z))}" +
            " storedAim=(%.2f,%.2f)".format(from.yaw, from.pitch) +
            " storedHit=${PathPlanDiagnostics.describeBlock(storedHit?.pos)} storedHitOk=${storedHit?.succeeded}" +
            " recomputedAim=${recomputed?.let { "(%.2f,%.2f)".format(it.first, it.second) } ?: "none"}" +
            " distance=%.2f range=%.1f".format(VecUtils.distance(from.pos, to.pos), range)
    }

    private fun isAimValid(eye: Vec3, target: EtherwarpNode, range: Double, aim: Aim, access: gobby.utils.skyblock.EtherwarpWorldAccess): Boolean =
        EtherwarpUtils.validateAim(eye, target.pos, range, aim.yaw to aim.pitch, access) != EtherwarpUtils.EtherPos.NONE

    private fun invalidEdge(snapshot: BlockCache.SnapshotView, tracked: BlockCache.SnapshotView): EdgeValidation {
        snapshot.mergeAccessedReads(tracked)
        val missing = tracked.missingChunksAccessed()
        return if (missing.isEmpty()) EdgeValidation.Invalid else EdgeValidation.Missing(missing)
    }

    private fun copyDetached(node: EtherwarpNode) = EtherwarpNode(node.x, node.y, node.z, node.pos, node.g, node.h, null, node.yaw, node.pitch)

    private sealed interface EdgeValidation {
        data class Valid(val node: EtherwarpNode, val dependency: EdgeDependency) : EdgeValidation
        data class Missing(val keys: Set<Long>) : EdgeValidation
        data object Invalid : EdgeValidation
    }
    private const val REJECTED_EDGE_SAMPLE = "edge rejected"
}
