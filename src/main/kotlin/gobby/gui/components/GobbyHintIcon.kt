package gobby.gui.components

import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.font.FontProvider

private const val RADIUS = 7f

class GobbyHintIcon(
    private val tooltip: GobbyTooltip,
    font: FontProvider? = null
) : UIRoundedRectangle(RADIUS) {

    init {
        constrain { color = ComponentTheme.HINT_ICON.toConstraint() }
        UIText("?", shadow = false).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            color = ComponentTheme.CHECK_MARK.toConstraint()
            font?.let { fontProvider = it }
        } childOf this
        tooltip.showOnHoverOf(this)
    }
}
