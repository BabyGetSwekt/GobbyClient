package gobby.gui.components

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.vigilance.gui.settings.SettingComponent
import org.lwjgl.glfw.GLFW
import java.awt.Color

class KeybindPropertyComponent(initialValue: Int) : SettingComponent() {

    private var currentKey: Int = initialValue
    private var listening = false

    private val buttonBackground by UIBlock(IDLE_COLOR).constrain {
        width = 80.pixels
        height = 20.pixels
    } childOf this

    private val label by UIText(getKeyName(currentKey)).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color.WHITE.toConstraint()
    } childOf buttonBackground

    init {
        constrain {
            width = 80.pixels
            height = 20.pixels
        }

        buttonBackground.onMouseClick {
            if (!listening) {
                startListening()
            } else {
                stopListening(-1)
            }
        }
    }

    fun onKeyPressed(keyCode: Int): Boolean {
        if (!listening) return false

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            stopListening(UNBOUND)
        } else {
            stopListening(keyCode)
        }
        return true
    }

    private fun startListening() {
        listening = true
        activeComponent = this
        buttonBackground.setColor(LISTENING_COLOR)
        label.setText("...")
    }

    private fun stopListening(keyCode: Int) {
        listening = false
        activeComponent = null
        currentKey = keyCode
        buttonBackground.setColor(IDLE_COLOR)
        label.setText(getKeyName(currentKey))
        changeValue(currentKey)
    }

    companion object {
        const val UNBOUND = -1

        private val IDLE_COLOR = Color(0, 0, 0, 120)
        private val LISTENING_COLOR = Color(255, 50, 50, 180)

        @JvmStatic
        var activeComponent: KeybindPropertyComponent? = null
            private set

        @JvmStatic
        var suppressNextChar = false

        @JvmStatic
        fun isListening(): Boolean = activeComponent != null

        @JvmStatic
        fun shouldSuppressChar(): Boolean {
            if (suppressNextChar) {
                suppressNextChar = false
                return true
            }
            return false
        }

        @JvmStatic
        fun handleKeyPress(keyCode: Int): Boolean {
            val handled = activeComponent?.onKeyPressed(keyCode) ?: false
            if (handled) suppressNextChar = true
            return handled
        }

        fun getKeyName(keyCode: Int): String {
            if (keyCode == -1) return "NONE"
            val name = GLFW.glfwGetKeyName(keyCode, 0)
            if (name != null) return name.uppercase()
            return when (keyCode) {
                GLFW.GLFW_KEY_LEFT_SHIFT -> "L.SHIFT"
                GLFW.GLFW_KEY_RIGHT_SHIFT -> "R.SHIFT"
                GLFW.GLFW_KEY_LEFT_CONTROL -> "L.CTRL"
                GLFW.GLFW_KEY_RIGHT_CONTROL -> "R.CTRL"
                GLFW.GLFW_KEY_LEFT_ALT -> "L.ALT"
                GLFW.GLFW_KEY_RIGHT_ALT -> "R.ALT"
                GLFW.GLFW_KEY_SPACE -> "SPACE"
                GLFW.GLFW_KEY_ENTER -> "ENTER"
                GLFW.GLFW_KEY_TAB -> "TAB"
                GLFW.GLFW_KEY_BACKSPACE -> "BACKSPACE"
                GLFW.GLFW_KEY_DELETE -> "DELETE"
                GLFW.GLFW_KEY_INSERT -> "INSERT"
                GLFW.GLFW_KEY_HOME -> "HOME"
                GLFW.GLFW_KEY_END -> "END"
                GLFW.GLFW_KEY_PAGE_UP -> "PAGE UP"
                GLFW.GLFW_KEY_PAGE_DOWN -> "PAGE DOWN"
                GLFW.GLFW_KEY_UP -> "UP"
                GLFW.GLFW_KEY_DOWN -> "DOWN"
                GLFW.GLFW_KEY_LEFT -> "LEFT"
                GLFW.GLFW_KEY_RIGHT -> "RIGHT"
                GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS LOCK"
                GLFW.GLFW_KEY_F1 -> "F1"
                GLFW.GLFW_KEY_F2 -> "F2"
                GLFW.GLFW_KEY_F3 -> "F3"
                GLFW.GLFW_KEY_F4 -> "F4"
                GLFW.GLFW_KEY_F5 -> "F5"
                GLFW.GLFW_KEY_F6 -> "F6"
                GLFW.GLFW_KEY_F7 -> "F7"
                GLFW.GLFW_KEY_F8 -> "F8"
                GLFW.GLFW_KEY_F9 -> "F9"
                GLFW.GLFW_KEY_F10 -> "F10"
                GLFW.GLFW_KEY_F11 -> "F11"
                GLFW.GLFW_KEY_F12 -> "F12"
                else -> "KEY $keyCode"
            }
        }
    }
}

fun Int.isKeybindSet(): Boolean = this != KeybindPropertyComponent.UNBOUND
