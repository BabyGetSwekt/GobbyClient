package gobby.pathfinder.etherwarp

import gobby.pathfinder.search.EtherwarpSearchOutcome
import gobby.pathfinder.world.BlockCache

internal fun PrimitiveSearchResult.toSearchOutcome(snapshot: BlockCache.SnapshotView): EtherwarpSearchOutcome =
    path?.let(EtherwarpSearchOutcome::Found) ?: when (termination) {
        PrimitiveSearchTermination.FRONTIER_EXHAUSTED -> EtherwarpSearchOutcome.NoRoute
        PrimitiveSearchTermination.TIMED_OUT -> EtherwarpSearchOutcome.TimedOut
        PrimitiveSearchTermination.INTERRUPTED -> EtherwarpSearchOutcome.Interrupted
        PrimitiveSearchTermination.MISSING_CHUNKS -> EtherwarpSearchOutcome.MissingChunks(snapshot.missingChunksAccessed())
        PrimitiveSearchTermination.UNAVAILABLE -> EtherwarpSearchOutcome.NoRoute
        PrimitiveSearchTermination.FOUND -> EtherwarpSearchOutcome.NoRoute
    }
