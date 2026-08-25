package gobby.gui.click

import gobby.utils.Utils
import org.lwjgl.glfw.GLFW

private const val NUMBER_SYMBOLS = ".-"

object NumberInput {

    const val MAX_LENGTH = 12

    fun sanitize(raw: String): String = raw.filter { it.isDigit() || it in NUMBER_SYMBOLS }

    fun isValid(text: String): Boolean = text.toFloatOrNull() != null

    fun begin(gui: ClickGUI, setting: NumberSetting) {
        gui.numberEditSetting = setting
        gui.numberField.reset(setting.display())
    }

    fun handleKey(gui: ClickGUI, setting: NumberSetting, key: Int): Boolean {
        val field = gui.numberField
        val ctrl = Modifiers.ctrl()
        when {
            key == GLFW.GLFW_KEY_ESCAPE -> gui.numberEditSetting = null
            key == GLFW.GLFW_KEY_ENTER -> commit(gui, setting)
            ctrl && key == GLFW.GLFW_KEY_A -> field.selectAll()
            ctrl && key == GLFW.GLFW_KEY_C -> Utils.setClipboard(field.selectedText())
            ctrl && key == GLFW.GLFW_KEY_V -> field.insert(Utils.getClipboard())
            ctrl && key == GLFW.GLFW_KEY_Z -> field.undo()
            key == GLFW.GLFW_KEY_BACKSPACE -> field.deleteBackward()
            key == GLFW.GLFW_KEY_DELETE -> field.deleteForward()
            key == GLFW.GLFW_KEY_LEFT -> field.placeCaret(field.caret - 1, Modifiers.shift())
            key == GLFW.GLFW_KEY_RIGHT -> field.placeCaret(field.caret + 1, Modifiers.shift())
            key == GLFW.GLFW_KEY_HOME -> field.placeCaret(0, Modifiers.shift())
            key == GLFW.GLFW_KEY_END -> field.placeCaret(field.text.length, Modifiers.shift())
        }
        return true
    }

    fun handleChar(gui: ClickGUI, chr: Char): Boolean {
        gui.numberField.insert(chr.toString())
        return true
    }

    private fun commit(gui: ClickGUI, setting: NumberSetting) {
        val parsed = gui.numberField.text.toFloatOrNull() ?: return
        setting.setSnapped(parsed)
        gui.numberEditSetting = null
        ConfigManager.save()
    }
}
