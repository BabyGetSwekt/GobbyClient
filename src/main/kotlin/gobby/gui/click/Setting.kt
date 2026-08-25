package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import org.lwjgl.glfw.GLFW
import java.awt.Color
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class Setting<T>(val name: String, val description: String, val defaultValue: T, val hidden: Boolean = false) {
    var value: T = defaultValue
    protected var dependency: (() -> Boolean)? = null
    internal var parentDropdown: DropDownSetting? = null

    val isVisible: Boolean get() = !hidden && (dependency?.invoke() != false)

    fun recordsUndo(): Boolean = this !is ActionSetting && this !is HudButton && this !is DropDownSetting
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

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): BooleanSetting {
        thisRef.settings.add(this)
        return this
    }
}

class NumberSetting(
    name: String,
    default: Float,
    val min: Float,
    val max: Float,
    val step: Float = 1f,
    val decimals: Int = 2,
    desc: String = "",
    hidden: Boolean = false
) : Setting<Float>(name, desc, snap(default, min, max, step), hidden), ReadWriteProperty<Any?, Int> {

    constructor(name: String, default: Int = 0, min: Int = 0, max: Int = 100, step: Int = 1, desc: String = "", hidden: Boolean = false) :
        this(name, default.toFloat(), min.toFloat(), max.toFloat(), step.toFloat(), 0, desc, hidden)

    val floatValue: Float get() = value
    val progress: Float get() = ((value - min) / (max - min)).coerceIn(0f, 1f)

    fun display(): String = if (decimals <= 0) value.roundToInt().toString() else String.format(Locale.US, "%.${decimals}f", value)

    fun setSnapped(v: Float) { value = snap(v, min, max, step) }

    fun setFromProgress(fraction: Float) = setSnapped(min + (max - min) * fraction)

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value.roundToInt()

    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Int) { setSnapped(v.toFloat()) }

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): NumberSetting {
        thisRef.settings.add(this)
        return this
    }

    companion object {
        private fun snap(v: Float, min: Float, max: Float, step: Float): Float =
            if (step <= 0f) v.coerceIn(min, max) else (min + ((v - min) / step).roundToInt() * step).coerceIn(min, max)
    }
}

class RangeSetting(
    name: String,
    defaultLow: Float,
    defaultHigh: Float,
    val min: Float,
    val max: Float,
    val increment: Float = 1f,
    desc: String = "",
    hidden: Boolean = false
) : Setting<ClosedFloatingPointRange<Float>>(name, desc, defaultLow..defaultHigh, hidden),
    ReadOnlyProperty<Any?, ClosedFloatingPointRange<Float>> {

    constructor(name: String, defaultLow: Int, defaultHigh: Int, min: Int, max: Int, increment: Int = 1, desc: String = "", hidden: Boolean = false) :
        this(name, defaultLow.toFloat(), defaultHigh.toFloat(), min.toFloat(), max.toFloat(), increment.toFloat(), desc, hidden)

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    init {
        val lo = snap(defaultLow).coerceIn(min, (max - increment).coerceAtLeast(min))
        value = lo..snap(defaultHigh).coerceIn((lo + increment).coerceAtMost(max), max)
    }

    var low: Float
        get() = value.start
        set(v) {
            val hi = value.endInclusive
            value = snap(v).coerceIn(min, (hi - increment).coerceAtLeast(min))..hi
        }

    var high: Float
        get() = value.endInclusive
        set(v) {
            val lo = value.start
            value = lo..snap(v).coerceIn((lo + increment).coerceAtMost(max), max)
        }

    fun progress(v: Float): Float = ((v - min) / (max - min)).coerceIn(0f, 1f)

    private fun snap(v: Float): Float = (min + ((v - min) / increment).roundToInt() * increment).coerceIn(min, max)

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): RangeSetting {
        thisRef.settings.add(this)
        return this
    }
}

class StringSetting(
    name: String,
    default: String = "",
    desc: String = "",
    hidden: Boolean = false,
    val length: Int = 50,
    val onCommit: (String) -> Unit = {}
) : Setting<String>(name, desc, default, hidden), ReadOnlyProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): StringSetting {
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

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

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

class DropDownSetting(
    name: String,
    desc: String = "",
    hidden: Boolean = false
) : Setting<Unit>(name, desc, Unit, hidden) {
    var expanded = false
    val children = mutableListOf<Setting<*>>()
}

class HudButton(
    name: String,
    desc: String = "",
    hidden: Boolean = false,
    val onClick: () -> Unit
) : Setting<Unit>(name, desc, Unit, hidden), ReadOnlyProperty<Any?, Unit> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) {}

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): HudButton {
        thisRef.settings.add(this)
        return this
    }
}
