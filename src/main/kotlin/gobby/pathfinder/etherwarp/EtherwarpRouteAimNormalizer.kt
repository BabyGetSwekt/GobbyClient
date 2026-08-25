package gobby.pathfinder.etherwarp

internal object EtherwarpRouteAimNormalizer {
    fun normalize(path: List<EtherwarpNode>): List<EtherwarpNode> = path.mapIndexed { index, node ->
        val child = path.getOrNull(index + 1)
        EtherwarpNode(
            node.x,
            node.y,
            node.z,
            node.pos,
            node.g,
            node.h,
            null,
            child?.yaw ?: ZERO_AIM,
            child?.pitch ?: ZERO_AIM
        )
    }

    private const val ZERO_AIM = 0f
}
