package gobby.utils.render

import gobby.utils.timer.Clock

private const val COMPLETE = 1f

class Animation(private val durationMs: Long, initial: Float = 0f) {

    init {
        require(durationMs > 0) { "Animation needs a positive duration" }
    }

    private val clock = Clock()
    private var origin = initial
    private var target = initial

    val value: Float get() = origin + (target - origin) * eased()

    val idle: Boolean get() = target == 0f && clock.getTime() >= durationMs

    fun to(next: Float) {
        if (next == target) return
        origin = value
        target = next
        clock.update()
    }

    fun set(on: Boolean) = to(if (on) COMPLETE else 0f)

    fun jumpTo(next: Float) {
        origin = next
        target = next
        clock.update()
    }

    fun lerp(from: Int, to: Int): Int = (from + (to - from) * value).toInt()

    private fun eased(): Float {
        val remaining = COMPLETE - (clock.getTime().toFloat() / durationMs).coerceIn(0f, COMPLETE)
        return COMPLETE - remaining * remaining * remaining
    }
}

class Animations(private val durationMs: Long) {

    private val byKey = HashMap<Any, Animation>()

    private fun of(key: Any): Animation = byKey.getOrPut(key) { Animation(durationMs) }

    fun toward(key: Any, on: Boolean): Animation = of(key).also { it.set(on) }
}
