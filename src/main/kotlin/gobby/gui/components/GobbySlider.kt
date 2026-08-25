package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import java.awt.Color

private const val KNOB_WIDTH = 3f
private const val KNOB_HALF = KNOB_WIDTH / 2f
private const val KNOB_OUTLINE_ALPHA = 160
private val TRANSPARENT = Color(0, 0, 0, 0)

class GobbySlider(
    trackColor: Color = ComponentTheme.INPUT_BG,
    private val maxFraction: Float = 1f,
    private val onFractionChange: (Float) -> Unit
) : UIBlock(trackColor) {

    val fill: UIComponent = UIContainer().constrain {
        width = 100.percent
        height = 100.percent
    } childOf this

    private val knob = UIBlock(Color.WHITE).constrain {
        width = KNOB_WIDTH.pixels
        height = 100.percent
    }.also { it.enableEffect(OutlineEffect(Color(0, 0, 0, KNOB_OUTLINE_ALPHA), 1f)) } childOf this

    private val catcher = UIBlock(TRANSPARENT).constrain {
        width = 100.percent
        height = 100.percent
    } childOf this

    private var dragging = false

    init {
        catcher.onMouseClick { event ->
            event.stopPropagation()
            dragging = true
            emit(event.relativeX)
        }
        catcher.onMouseDrag { mouseX, _, _ -> if (dragging) emit(mouseX) }
        catcher.onMouseRelease { dragging = false }
    }

    fun setFraction(value: Float) {
        knob.setX((value.coerceIn(0f, maxFraction) * trackWidth() - KNOB_HALF).pixels)
    }

    private fun trackWidth(): Float = getWidth()

    private fun emit(relativeX: Float) {
        onFractionChange((relativeX / trackWidth()).coerceIn(0f, maxFraction))
    }
}
