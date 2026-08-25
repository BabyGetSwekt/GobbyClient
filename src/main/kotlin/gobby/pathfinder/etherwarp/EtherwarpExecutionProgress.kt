package gobby.pathfinder.etherwarp

internal fun nextAwaitExecutionIndex(currentIndex: Int, landingLabel: Int, nodeCount: Int): Int =
    maxOf(currentIndex, landingLabel + 1).coerceAtMost(nodeCount)
