package gobby.gui.components

import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.font.FontProvider
import java.awt.Color

private const val DEFAULT_TEXT_SCALE = 0.85f

class GobbyButton(
    text: String,
    baseColor: Color,
    hoverColor: Color,
    scale: Float = DEFAULT_TEXT_SCALE,
    font: FontProvider? = null,
    private val onClick: () -> Unit
) : UIRoundedRectangle(ComponentTheme.CORNER_RADIUS) {

    init {
        constrain { color = baseColor.toConstraint() }

        UIText(text, shadow = true).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            color = ComponentTheme.TEXT.toConstraint()
            textScale = scale.pixels
            font?.let { fontProvider = it }
        } childOf this

        applyHoverColor(baseColor, hoverColor)
        onMouseClick { event ->
            event.stopPropagation()
            onClick()
        }
    }
}
