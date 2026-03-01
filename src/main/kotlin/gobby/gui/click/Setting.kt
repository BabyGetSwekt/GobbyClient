package gobby.gui.click

import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class Setting<T>(val name: String, val description: String, default: T, val hidden: Boolean = false) {
    var value: T = default
    protected var dependency: (() -> Boolean)? = null

    val isVisible: Boolean get() = !hidden && (dependency?.invoke() != false)
}

class BooleanSetting(
    name: String,
    default: Boolean = false,
    desc: String = "",
    hidden: Boolean = false
) : Setting<Boolean>(name, desc, default, hidden), ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Boolean) { value = v }
    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): BooleanSetting {
        thisRef.settings.add(this)
        return this
    }
}

class NumberSetting(
    name: String,
    default: Int = 0,
    val min: Int = 0,
    val max: Int = 100,
    val step: Int = 1,
    desc: String = "",
    hidden: Boolean = false
) : Setting<Int>(name, desc, default, hidden), ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Int) { value = v.coerceIn(min, max) }
    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): NumberSetting {
        thisRef.settings.add(this)
        return this
    }
}

class SelectorSetting(
    name: String,
    default: Int = 0,
    val options: List<String>,
    desc: String = "",
    hidden: Boolean = false
) : Setting<Int>(name, desc, default, hidden), ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Int) { value = v.coerceIn(0, options.lastIndex) }
    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): SelectorSetting {
        thisRef.settings.add(this)
        return this
    }
}

class ColorSetting(
    name: String,
    default: Color = Color.WHITE,
    desc: String = "",
    hidden: Boolean = false,
    var expanded: Boolean = false,
    var cachedHue: Float = -1f
) : Setting<Color>(name, desc, default, hidden), ReadWriteProperty<Any?, Color> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Color) { value = v }
    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): ColorSetting {
        thisRef.settings.add(this)
        return this
    }
}

class ActionSetting(
    name: String,
    desc: String = "",
    hidden: Boolean = false,
    val action: () -> Unit
) : Setting<Unit>(name, desc, Unit, hidden), ReadOnlyProperty<Any?, Unit> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) {}
    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): ActionSetting {
        thisRef.settings.add(this)
        return this
    }
}

class KeybindSetting(
    name: String = "Toggle Key",
    desc: String = "Press a key to bind",
    hidden: Boolean = false
) : Setting<Int>(name, desc, 0, hidden), ReadWriteProperty<Any?, Int> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Int) { value = v }
    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): KeybindSetting {
        thisRef.settings.add(this)
        return this
    }

    fun getKeyName(): String {
        if (value == 0) return "None"
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
