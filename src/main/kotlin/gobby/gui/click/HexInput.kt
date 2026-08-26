package gobby.gui.click

import gobby.utils.Utils
import org.lwjgl.glfw.GLFW

private const val HUE_UNSET = -1f

object HexInput {

    fun handleKey(gui: ClickGUI, setting: ColorSetting, key: Int): Boolean {
        val ctrl = Modifiers.ctrl()
        when {
            key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER -> close(gui)
            ctrl && key == GLFW.GLFW_KEY_C -> copy(gui, setting)
            ctrl && key == GLFW.GLFW_KEY_V -> paste(gui, setting)
            TextFieldKeys.handle(gui.hexField, key) -> apply(gui, setting)
        }
        return true
    }

    fun handleChar(gui: ClickGUI, setting: ColorSetting, chr: Char): Boolean {
        gui.hexField.insert(chr.toString())
        apply(gui, setting)
        return true
    }

    fun copy(gui: ClickGUI, setting: ColorSetting) = Utils.setClipboard(copyText(gui, setting))

    fun paste(gui: ClickGUI, setting: ColorSetting) {
        gui.hexField.replaceAll(Utils.getClipboard())
        commit(gui, setting)
    }

    private fun copyText(gui: ClickGUI, setting: ColorSetting): String =
        gui.hexField.text.takeIf { HexColor.isComplete(it) } ?: HexColor.format(setting.value)

    private fun apply(gui: ClickGUI, setting: ColorSetting) {
        HexColor.parse(gui.hexField.text)?.let {
            setting.value = it
            setting.cachedHue = HUE_UNSET
        }
    }

    private fun commit(gui: ClickGUI, setting: ColorSetting) {
        setting.value = HexColor.parse(gui.hexField.text) ?: setting.defaultValue
        setting.cachedHue = HUE_UNSET
        ConfigManager.save()
    }

    private fun close(gui: ClickGUI) {
        gui.hexEditSetting = null
    }
}
