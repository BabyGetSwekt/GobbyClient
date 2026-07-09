package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import java.awt.Color

private const val ENTER_DURATION = 0.15f
private const val EXIT_DURATION = 0.2f

fun UIComponent.applyHoverColor(base: Color, hover: Color) {
    onMouseEnter { animate { setColorAnimation(Animations.OUT_EXP, ENTER_DURATION, hover.toConstraint()) } }
    onMouseLeave { animate { setColorAnimation(Animations.OUT_EXP, EXIT_DURATION, base.toConstraint()) } }
}
