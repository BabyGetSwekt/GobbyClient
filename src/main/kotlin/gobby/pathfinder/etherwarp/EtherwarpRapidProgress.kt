package gobby.pathfinder.etherwarp

internal data class EtherwarpRapidProgress(val furthestIndex: Int, val progressTick: Int)

internal fun advanceRapidProgress(
    current: EtherwarpRapidProgress,
    matchedIndex: Int?,
    observedTick: Int
): EtherwarpRapidProgress = if (matchedIndex != null && matchedIndex > current.furthestIndex) {
    EtherwarpRapidProgress(matchedIndex, observedTick)
} else {
    current
}

internal fun shouldRecoverRapidPartial(
    furthestIndex: Int,
    lastIndex: Int,
    ticksSinceProgress: Int
): Boolean = furthestIndex in 1 until lastIndex && ticksSinceProgress >= RAPID_PROGRESS_TIMEOUT_TICKS
