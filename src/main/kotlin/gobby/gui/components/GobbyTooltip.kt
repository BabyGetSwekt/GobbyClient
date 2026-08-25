package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.ChildBasedSizeConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.font.FontProvider
import java.awt.Color

private const val HEIGHT = 16f
private const val HORIZONTAL_PADDING = 12f

class GobbyTooltip(
    parent: UIComponent,
    text: String,
    font: FontProvider? = null,
    textColor: Color = ComponentTheme.WARNING
) : UIRoundedRectangle(ComponentTheme.CORNER_RADIUS) {

    init {
        this childOf parent
        constrain {
            width = ChildBasedSizeConstraint() + HORIZONTAL_PADDING.pixels
            height = HEIGHT.pixels
            color = ComponentTheme.TOOLTIP_BG.toConstraint()
        }
        UIText(text, shadow = true).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            color = textColor.toConstraint()
            textScale = ComponentTheme.SMALL_SCALE.pixels
            font?.let { fontProvider = it }
        } childOf this
        hide(instantly = true)
    }

    fun showOnHoverOf(trigger: UIComponent) {
        trigger.onMouseEnter { unhide() }
        trigger.onMouseLeave { hide() }
    }
}
