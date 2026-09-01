package gobby.gui.click

import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

sealed class Setting<T>(val name: String, val description: String, val defaultValue: T, val hidden: Boolean = false) {
    var value: T = defaultValue
    protected var dependency: (() -> Boolean)? = null
    internal var parentDropdown: DropDownSetting? = null
    internal var section: SettingSection? = null

    val isVisible: Boolean get() = !hidden && (dependency?.invoke() != false)

    fun recordsUndo(): Boolean = when (this) {
        is ActionSetting, is HudButton, is DropDownSetting, is ModelPreviewSetting, is TextSetting, is RefreshSetting -> false
        else -> true
    }
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

class DropDownSetting(
    name: String,
    desc: String = "",
    hidden: Boolean = false
) : Setting<Unit>(name, desc, Unit, hidden) {
    var expanded = false
    val children = mutableListOf<Setting<*>>()
    internal val ownSection = SettingSection(name)
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

class TextSetting(
    name: String,
    desc: String = "",
    hidden: Boolean = false,
    val text: () -> String
) : Setting<Unit>(name, desc, Unit, hidden), ReadOnlyProperty<Any?, Unit> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) {}

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): TextSetting {
        thisRef.settings.add(this)
        return this
    }
}

class RefreshSetting(
    name: String,
    desc: String = "",
    hidden: Boolean = false,
    val busy: () -> Boolean = { false },
    val action: () -> Unit
) : Setting<Unit>(name, desc, Unit, hidden), ReadOnlyProperty<Any?, Unit> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) {}

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): RefreshSetting {
        thisRef.settings.add(this)
        return this
    }
}
