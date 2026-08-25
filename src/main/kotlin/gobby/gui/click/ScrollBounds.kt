package gobby.gui.click

internal object ScrollBounds {

    fun lowest(totalHeight: Int, viewport: Int): Float =
        0f - (totalHeight - viewport).coerceAtLeast(0).toFloat()

    fun clamp(offset: Float, totalHeight: Int, viewport: Int): Float =
        offset.coerceIn(lowest(totalHeight, viewport), 0f)
}
