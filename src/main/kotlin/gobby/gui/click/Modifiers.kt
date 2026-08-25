package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import org.lwjgl.glfw.GLFW

object Modifiers {

    fun ctrl(): Boolean = anyPressed(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)

    fun shift(): Boolean = anyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)

    private fun anyPressed(vararg keys: Int): Boolean {
        val handle = mc.window.handle()
        return keys.any { GLFW.glfwGetKey(handle, it) == GLFW.GLFW_PRESS }
    }
}
