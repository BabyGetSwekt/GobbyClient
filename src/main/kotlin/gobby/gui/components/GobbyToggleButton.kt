package gobby.gui.components

import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.font.FontProvider
import java.awt.Color

class GobbyToggleButton(
    private val activeText: String,
    private val inactiveText: String,
    private val activeColor: Color = ComponentTheme.DANGER,
    private val inactiveColor: Color = ComponentTheme.ACCENT_OFF,
    font: FontProvider? = null,
    private val onToggle: (Boolean) -> Unit
) : UIRoundedRectangle(ComponentTheme.CORNER_RADIUS) {

    private var active = false

    private val label = UIText(inactiveText, shadow = true).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = ComponentTheme.TEXT_DIM.toConstraint()
        font?.let { fontProvider = it }
    } childOf this

    init {
        constrain { color = inactiveColor.toConstraint() }
        onMouseClick { event ->
            event.stopPropagation()
            setActive(!active)
            onToggle(active)
        }
    }

    fun setActive(value: Boolean) {
        active = value
        setColor(if (value) activeColor else inactiveColor)
        label.setText(if (value) activeText else inactiveText)
    }
}
