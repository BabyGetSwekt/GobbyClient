package gobby.gui.click

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private const val DEFAULT_YAW = 0f
private const val DEFAULT_PITCH = 0f
private const val DEFAULT_ZOOM = 1f
private const val MIN_ZOOM = 0.55f
private const val MAX_ZOOM = 2.6f
private const val ZOOM_STEP = 0.12f
private const val YAW_PER_PIXEL = 2.2f
private const val PITCH_PER_PIXEL = 1.4f
private const val PITCH_LIMIT = 35f

class ModelPreviewSetting(
    name: String,
    val color: ColorSetting,
    desc: String = "",
    hidden: Boolean = false
) : Setting<Unit>(name, desc, Unit, hidden), ReadOnlyProperty<Any?, Unit> {

    internal var yaw = DEFAULT_YAW
        private set
    internal var pitch = DEFAULT_PITCH
        private set
    internal var zoom = DEFAULT_ZOOM
        private set

    override fun getValue(thisRef: Any?, property: KProperty<*>) {}

    fun resetView() {
        yaw = DEFAULT_YAW
        pitch = DEFAULT_PITCH
        zoom = DEFAULT_ZOOM
    }

    fun rotate(dx: Double, dy: Double) {
        yaw -= dx.toFloat() * YAW_PER_PIXEL
        pitch = (pitch - dy.toFloat() * PITCH_PER_PIXEL).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
    }

    fun zoomBy(amount: Double) {
        zoom = (zoom + amount.toFloat() * ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): ModelPreviewSetting {
        thisRef.settings.add(this)
        return this
    }
}
