package gobby.utils.render

import gobby.Gobbyclient.Companion.mc
import org.lwjgl.glfw.GLFW

private const val DEFAULT_CURSOR = 0L

object CursorStyle {

    private var handCursor = DEFAULT_CURSOR
    private var applied = DEFAULT_CURSOR
    private var wantsHand = false

    fun requestHand() {
        wantsHand = true
    }

    fun requestHandIf(hovered: Boolean) {
        if (hovered) wantsHand = true
    }

    fun apply() {
        val target = if (wantsHand) hand() else DEFAULT_CURSOR
        wantsHand = false
        if (target == applied) return
        GLFW.glfwSetCursor(mc.window.handle(), target)
        applied = target
    }

    fun reset() {
        if (applied == DEFAULT_CURSOR) return
        GLFW.glfwSetCursor(mc.window.handle(), DEFAULT_CURSOR)
        applied = DEFAULT_CURSOR
    }

    private fun hand(): Long {
        if (handCursor == DEFAULT_CURSOR) handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_POINTING_HAND_CURSOR)
        return handCursor
    }
}
