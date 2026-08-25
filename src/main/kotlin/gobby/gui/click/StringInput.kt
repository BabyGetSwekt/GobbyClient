package gobby.gui.click

import gobby.utils.Utils
import org.lwjgl.glfw.GLFW

private const val ALLOWED_SYMBOLS = "._:/-"

object StringInput {

    const val DEFAULT_MAX_LENGTH = 64

    fun isAllowedChar(chr: Char): Boolean = chr.isLetterOrDigit() || chr in ALLOWED_SYMBOLS

    fun sanitize(raw: String): String = raw.filter(::isAllowedChar)

    fun begin(gui: ClickGUI, setting: StringSetting) {
        gui.stringEditSetting = setting
        gui.stringField.maxLength = setting.length
        gui.stringField.reset(setting.value)
    }

    fun commit(gui: ClickGUI, setting: StringSetting) {
        setting.value = gui.stringField.text.trim()
        gui.stringEditSetting = null
        setting.onCommit(setting.value)
        ConfigManager.save()
    }

    fun handleKey(gui: ClickGUI, setting: StringSetting, key: Int): Boolean {
        val field = gui.stringField
        val ctrl = Modifiers.ctrl()
        when {
            key == GLFW.GLFW_KEY_ESCAPE -> gui.stringEditSetting = null
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
        gui.stringField.insert(chr.toString())
        return true
    }
}
