package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import org.lwjgl.glfw.GLFW

class KeybindSetting(
    name: String = "Toggle Key",
    desc: String = "Press a key to bind",
    hidden: Boolean = false,
    val notification: Boolean = false
) : Setting<Int>(name, desc, 0, hidden), ReadWriteProperty<Any?, Int> {
    companion object { const val MOUSE_OFFSET = 1000 }

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Int) { value = v }

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun withDependency(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): KeybindSetting {
        thisRef.settings.add(this)
        return this
    }

    fun isPressed(): Boolean {
        if (value == 0) return false
        val handle = mc.window.handle()
        return if (value >= MOUSE_OFFSET) GLFW.glfwGetMouseButton(handle, value - MOUSE_OFFSET) == GLFW.GLFW_PRESS
        else GLFW.glfwGetKey(handle, value) == GLFW.GLFW_PRESS
    }

    fun getKeyName(): String {
        if (value == 0) return "None"
        if (value >= MOUSE_OFFSET) return when (value - MOUSE_OFFSET) {
            GLFW.GLFW_MOUSE_BUTTON_LEFT -> "LMB"
            GLFW.GLFW_MOUSE_BUTTON_RIGHT -> "RMB"
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> "MMB"
            else -> "M${value - MOUSE_OFFSET + 1}"
        }
        val name = GLFW.glfwGetKeyName(value, 0)
        return name?.uppercase() ?: when (value) {
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
                "F${value - GLFW.GLFW_KEY_F1 + 1}"
            else -> "KEY $value"
        }
    }
}
