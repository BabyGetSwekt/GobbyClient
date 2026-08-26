package gobby.gui.click

import gobby.utils.Utils
import org.lwjgl.glfw.GLFW

internal object TextFieldKeys {

    fun handle(field: TextField, key: Int, allowUndo: Boolean = true): Boolean {
        val ctrl = Modifiers.ctrl()
        when {
            ctrl && key == GLFW.GLFW_KEY_A -> field.selectAll()
            ctrl && key == GLFW.GLFW_KEY_C -> Utils.setClipboard(field.selectedText())
            ctrl && key == GLFW.GLFW_KEY_V -> field.insert(Utils.getClipboard())
            ctrl && key == GLFW.GLFW_KEY_Z && allowUndo -> field.undo()
            key == GLFW.GLFW_KEY_BACKSPACE -> field.deleteBackward()
            key == GLFW.GLFW_KEY_DELETE -> field.deleteForward()
            key == GLFW.GLFW_KEY_LEFT -> field.placeCaret(field.caret - 1, Modifiers.shift())
            key == GLFW.GLFW_KEY_RIGHT -> field.placeCaret(field.caret + 1, Modifiers.shift())
            key == GLFW.GLFW_KEY_HOME -> field.placeCaret(0, Modifiers.shift())
            key == GLFW.GLFW_KEY_END -> field.placeCaret(field.text.length, Modifiers.shift())
            else -> return false
        }
        return true
    }
}
