package gobby.gui.components

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.dsl.*
import java.awt.Color

private const val CHEVRON_STEPS = 5
private const val CHEVRON_DOT = 1.6f
private const val CHEVRON_HALF_DOT = CHEVRON_DOT / 2f
private const val ARM_SPAN = 50f
private const val FULL_SPAN = 100f

class GobbyChevron(private val color: Color = ComponentTheme.TEXT_DIM) : UIContainer() {

    init {
        (0..CHEVRON_STEPS).forEach { step ->
            val progress = ARM_SPAN * step / CHEVRON_STEPS
            val depth = FULL_SPAN * step / CHEVRON_STEPS
            dot(progress, depth)
            dot(FULL_SPAN - progress, depth)
        }
    }

    private fun dot(xPercent: Float, yPercent: Float) {
        UIBlock(color).constrain {
            x = xPercent.percent - CHEVRON_HALF_DOT.pixels
            y = yPercent.percent - CHEVRON_HALF_DOT.pixels
            width = CHEVRON_DOT.pixels
            height = CHEVRON_DOT.pixels
        } childOf this
    }
}
