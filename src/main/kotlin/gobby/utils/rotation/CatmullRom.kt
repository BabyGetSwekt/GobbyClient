package gobby.utils.rotation

object CatmullRom {

    const val UNIFORM_TENSION = 0.5f

    fun interpolate(before: Float, from: Float, to: Float, after: Float, t: Float): Float {
        val squared = t * t
        val cubed = squared * t
        return 0.5f * (
            2f * from +
                (-before + to) * t +
                (2f * before - 5f * from + 4f * to - after) * squared +
                (-before + 3f * from - 3f * to + after) * cubed
            )
    }

    fun withTension(before: Float, from: Float, to: Float, after: Float, t: Float, tension: Float): Float {
        val startTangent = tension * (to - before)
        val endTangent = tension * (after - from)
        val squared = t * t
        val cubed = squared * t
        return (2f * cubed - 3f * squared + 1f) * from +
            (cubed - 2f * squared + t) * startTangent +
            (-2f * cubed + 3f * squared) * to +
            (cubed - squared) * endTangent
    }

    fun settle(before: Float, from: Float, to: Float, t: Float): Float =
        interpolate(before, from, to, from, t)
}
