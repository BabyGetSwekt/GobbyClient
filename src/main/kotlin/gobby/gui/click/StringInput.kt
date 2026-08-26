package gobby.gui.click

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
        when (key) {
            GLFW.GLFW_KEY_ESCAPE -> gui.stringEditSetting = null
            GLFW.GLFW_KEY_ENTER -> commit(gui, setting)
            else -> TextFieldKeys.handle(gui.stringField, key)
        }
        return true
    }

    fun handleChar(gui: ClickGUI, chr: Char): Boolean {
        gui.stringField.insert(chr.toString())
        return true
    }
}
