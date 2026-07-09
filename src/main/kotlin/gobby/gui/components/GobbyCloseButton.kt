package gobby.gui.components

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.dsl.*

private const val X_STEPS = 6
private const val X_DOT = 2f
private const val X_HALF_DOT = X_DOT / 2f
private const val INSET_PERCENT = 28f
private const val SPAN_PERCENT = 100f - INSET_PERCENT * 2f

class GobbyCloseButton(
    private val onClose: () -> Unit
) : UIRoundedRectangle(ComponentTheme.CORNER_RADIUS) {

    init {
        constrain { color = ComponentTheme.DANGER.toConstraint() }

        (0..X_STEPS).forEach { step ->
            val progress = SPAN_PERCENT * step / X_STEPS
            dotAt(INSET_PERCENT + progress, INSET_PERCENT + progress)
            dotAt(INSET_PERCENT + progress, 100f - INSET_PERCENT - progress)
        }

        applyHoverColor(ComponentTheme.DANGER, ComponentTheme.DANGER_HOVER)
        onMouseClick { event ->
            event.stopPropagation()
            onClose()
        }
    }

    private fun dotAt(xPercent: Float, yPercent: Float) {
        UIBlock(ComponentTheme.TEXT).constrain {
            x = xPercent.percent - X_HALF_DOT.pixels
            y = yPercent.percent - X_HALF_DOT.pixels
            width = X_DOT.pixels
            height = X_DOT.pixels
        } childOf this
    }
}
