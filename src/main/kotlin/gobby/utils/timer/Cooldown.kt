package gobby.utils.timer

class Cooldown {

    private val clock = Clock()
    private var durationMs = 0L

    val remainingMs: Long get() = (durationMs - clock.getTime()).coerceAtLeast(0L)

    val isActive: Boolean get() = remainingMs > 0L

    val remainingSeconds: Double get() = remainingMs / 1000.0

    fun start(seconds: Int) {
        durationMs = seconds * 1000L
        clock.update()
    }

    fun clear() {
        durationMs = 0L
    }
}
