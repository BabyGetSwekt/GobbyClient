package gobby.pathfinder.navigation

internal object AtlasGraphDiagnostics {

    fun describeGraph(rooms: List<PreparedGraphRoom>, portals: List<PreparedPortal>, proposal: GraphProposal): String =
        "atlas graph rooms=${rooms.map(::describeRoom)} portals=${portals.map(::describePortal)}" +
            " candidates=${proposal.candidateCount} attemptedEdges=${proposal.attemptedEdges}" +
            " validEdges=${proposal.validEdges} route=${proposal.route.size} stop=${proposal.backboneStop}"

    fun describePreparation(portals: List<PreparedPortal>, ingress: Boolean, connected: List<Boolean>, goal: Boolean): String =
        "atlas graph prep ingress=$ingress goal=$goal portals=" +
            portals.map { "${it.fromLabel}->${it.toLabel}" }.zip(connected).joinToString()

    private fun describeRoom(room: PreparedGraphRoom): String =
        "${room.label}:${room.positions.size}${if (room.runtimeBridge) "/runtime" else ""}"

    private fun describePortal(portal: PreparedPortal): String =
        "${portal.fromLabel}->${portal.toLabel}:${portal.fromCandidates.size}/${portal.toCandidates.size}"
}
