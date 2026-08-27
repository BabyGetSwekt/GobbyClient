package gobby.gui.click

import org.lwjgl.glfw.GLFW

const val MOUSE_OFFSET = 1000

object KeyNames {

    fun of(key: Int): String {
        if (key == 0) return "None"
        if (key >= MOUSE_OFFSET) return when (key - MOUSE_OFFSET) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB"
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB"
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB"
            else -> "M${key - MOUSE_OFFSET + 1}"
        }
        val name = GLFW.glfwGetKeyName(key, 0)
        return name?.uppercase() ?: when (key) {
            GLFW.GLFW_KEY_LEFT_SHIFT -> "L-SHIFT"
            GLFW.GLFW_KEY_RIGHT_SHIFT -> "R-SHIFT"
            GLFW.GLFW_KEY_LEFT_CONTROL -> "L-CTRL"
            GLFW.GLFW_KEY_RIGHT_CONTROL -> "R-CTRL"
            GLFW.GLFW_KEY_LEFT_ALT -> "L-ALT"
            GLFW.GLFW_KEY_RIGHT_ALT -> "R-ALT"
            GLFW.GLFW_KEY_TAB -> "TAB"
            GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS"
            GLFW.GLFW_KEY_SPACE -> "SPACE"
            GLFW.GLFW_KEY_ENTER -> "ENTER"
            GLFW.GLFW_KEY_BACKSPACE -> "BACK"
            GLFW.GLFW_KEY_DELETE -> "DEL"
            GLFW.GLFW_KEY_INSERT -> "INS"
            GLFW.GLFW_KEY_HOME -> "HOME"
            GLFW.GLFW_KEY_END -> "END"
            GLFW.GLFW_KEY_PAGE_UP -> "PGUP"
            GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN"
            GLFW.GLFW_KEY_UP -> "UP"
            GLFW.GLFW_KEY_DOWN -> "DOWN"
            GLFW.GLFW_KEY_LEFT -> "LEFT"
            GLFW.GLFW_KEY_RIGHT -> "RIGHT"
            in GLFW.GLFW_KEY_F1..GLFW.GLFW_KEY_F25 ->
                "F${key - GLFW.GLFW_KEY_F1 + 1}"
            else -> "KEY $key"
        }
    }
}
