package gobby.gui.click

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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
