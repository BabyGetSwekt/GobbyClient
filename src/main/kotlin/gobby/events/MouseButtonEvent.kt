package gobby.events

class MouseButtonEvent(val button: Int, val action: Int) : Events() {
    companion object {
        const val PRESS = 1
        const val RELEASE = 0
        const val RIGHT_BUTTON = 1
    }
}
