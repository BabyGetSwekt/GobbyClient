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
    companion object { const val MOUSE_OFFSET = gobby.gui.click.MOUSE_OFFSET }

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

    fun getKeyName(): String = KeyNames.of(value)
}
