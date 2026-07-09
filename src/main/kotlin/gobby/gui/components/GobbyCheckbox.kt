package gobby.gui.components

import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect

private const val CORNER = 2f
private const val MARK_SIZE_PERCENT = 55f

class GobbyCheckbox(
    initial: Boolean,
    private val onToggle: (Boolean) -> Unit
) : UIRoundedRectangle(CORNER) {

    var checked = initial
        private set

    private val mark = UIRoundedRectangle(CORNER).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        width = MARK_SIZE_PERCENT.percent
        height = MARK_SIZE_PERCENT.percent
        color = ComponentTheme.CHECK_MARK.toConstraint()
    } childOf this

    init {
        constrain { color = colorFor(checked).toConstraint() }
        enableEffect(OutlineEffect(ComponentTheme.BORDER, 1f))
        if (!checked) mark.hide(instantly = true)
        onMouseClick { event ->
            event.stopPropagation()
            setChecked(!checked)
        }
    }

    fun setChecked(value: Boolean) {
        checked = value
        setColor(colorFor(value))
        if (value) mark.unhide() else mark.hide(instantly = true)
        onToggle(value)
    }

    private fun colorFor(value: Boolean) = if (value) ComponentTheme.ACCENT else ComponentTheme.ACCENT_OFF
}
