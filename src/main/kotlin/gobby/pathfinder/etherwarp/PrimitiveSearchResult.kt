package gobby.pathfinder.etherwarp

internal enum class PrimitiveSearchTermination {
    FOUND,
    FRONTIER_EXHAUSTED,
    TIMED_OUT,
    INTERRUPTED,
    MISSING_CHUNKS,
    UNAVAILABLE
}

internal data class PrimitiveSearchResult(
    val path: List<EtherwarpNode>?,
    val termination: PrimitiveSearchTermination
)
