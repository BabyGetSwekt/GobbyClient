package gobby.gui.click

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
        when (key) {
            GLFW.GLFW_KEY_ESCAPE -> gui.numberEditSetting = null
            GLFW.GLFW_KEY_ENTER -> commit(gui, setting)
            else -> TextFieldKeys.handle(gui.numberField, key)
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
