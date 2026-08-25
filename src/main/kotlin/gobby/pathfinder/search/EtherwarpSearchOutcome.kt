package gobby.pathfinder.search

import gobby.pathfinder.etherwarp.EtherwarpNode

sealed interface EtherwarpSearchOutcome {
    data class Found(override val path: List<EtherwarpNode>) : EtherwarpSearchOutcome
    data object TimedOut : EtherwarpSearchOutcome
    data object Interrupted : EtherwarpSearchOutcome
    data class MissingChunks(val keys: Set<Long>) : EtherwarpSearchOutcome
    data object NoRoute : EtherwarpSearchOutcome

    val path: List<EtherwarpNode>?
        get() = (this as? Found)?.path
}
