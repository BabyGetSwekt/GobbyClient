package gobby.pathfinder.navigation

import net.minecraft.core.BlockPos

internal data class PreparedGraphRoom(
    val canonical: Int,
    val label: String,
    val positions: List<BlockPos>,
    val anchors: List<BlockPos>,
    val edges: List<PreparedDirectedEdge>,
    val outgoing: Map<BlockPos, List<PreparedDirectedEdge>> = emptyMap(),
    val runtimeBridge: Boolean = false,
    val runtimeSeeds: List<BlockPos> = emptyList(),
    val liveConnectors: List<BlockPos> = emptyList()
)

internal data class PreparedPortal(
    val fromCanonical: Int,
    val toCanonical: Int,
    val fromLabel: String,
    val toLabel: String,
    val fromSeed: BlockPos,
    val toSeed: BlockPos,
    val fromCandidates: List<BlockPos> = emptyList(),
    val toCandidates: List<BlockPos> = emptyList()
) {
    val candidates: List<BlockPos> get() = (fromCandidates + toCandidates).distinct()
}

internal data class GraphProposal(
    val route: List<gobby.pathfinder.etherwarp.EtherwarpNode>,
    val candidateCount: Int,
    val attemptedEdges: Int,
    val validEdges: Int,
    val backboneStop: String? = null,
    val validatedRoute: DependencyValidatedRoute? = null,
    val completenessCertified: Boolean = false,
    val compatiblePreparedRoute: Boolean = false
)
