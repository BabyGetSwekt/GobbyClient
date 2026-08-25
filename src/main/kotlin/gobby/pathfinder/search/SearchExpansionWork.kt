package gobby.pathfinder.search

internal data class SearchCandidateSequence(val indices: IntArray)

internal class SearchExpansionWork(
    val nodeIndex: Int,
    val landingGeneration: Int,
    private val sequence: SearchCandidateSequence
) {
    private var cursor = 0
    val done: Boolean get() = cursor >= sequence.indices.size

    inline fun consume(maximum: Int, shouldContinue: () -> Boolean, visitor: (Int) -> Boolean): Boolean {
        var consumed = 0
        while (consumed < maximum && cursor < sequence.indices.size && shouldContinue()) {
            val position = cursor++
            consumed++
            if (visitor(sequence.indices[position])) return true
        }
        return false
    }
}
