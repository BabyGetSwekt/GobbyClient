package gobby.pathfinder.etherwarp

import gobby.utils.timer.Clock

class SearchDeadline(private val budgetMs: Long) {
    private val clock = Clock()

    val elapsed: Long get() = clock.getTime()
    val expired: Boolean get() = clock.hasTimePassed(budgetMs)
}
