package gobby.gui.click

import java.awt.Color
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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
