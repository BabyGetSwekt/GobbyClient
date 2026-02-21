package gobby.gui.components

import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import java.awt.Color

class ModIdEntryComponent(
    val modId: String,
    private val onRemove: (ModIdEntryComponent) -> Unit
) : UIRoundedRectangle(3f) {

    init {
        constrain {
            width = 100.percent
            height = 18.pixels
            color = BASE_COLOR.toConstraint()
        }

        UIText(modId, shadow = true).constrain {
            x = 6.pixels
            y = CenterConstraint()
            color = Color(200, 200, 210).toConstraint()
        } childOf this

        val removeBtn = UIRoundedRectangle(2f).constrain {
            x = 4.pixels(alignOpposite = true)
            y = CenterConstraint()
            width = 14.pixels
            height = 14.pixels
            color = REMOVE_COLOR.toConstraint()
        } childOf this

        UIText("x", shadow = false).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            color = Color(220, 220, 220).toConstraint()
        } childOf removeBtn

        removeBtn.applyHoverAnimation(REMOVE_COLOR, REMOVE_HOVER_COLOR)
        removeBtn.onMouseClick { event ->
            event.stopPropagation()
            onRemove(this@ModIdEntryComponent)
        }

        applyHoverAnimation(BASE_COLOR, HOVER_COLOR)
    }

    companion object {
        private val BASE_COLOR = Color(26, 26, 32, 255)
        private val HOVER_COLOR = Color(34, 34, 42, 255)
        private val REMOVE_COLOR = Color(120, 30, 30, 200)
        private val REMOVE_HOVER_COLOR = Color(180, 40, 40, 240)

        private fun UIRoundedRectangle.applyHoverAnimation(baseColor: Color, hoverColor: Color) {
            onMouseEnter {
                animate { setColorAnimation(Animations.OUT_EXP, 0.15f, hoverColor.toConstraint()) }
            }
            onMouseLeave {
                animate { setColorAnimation(Animations.OUT_EXP, 0.2f, baseColor.toConstraint()) }
            }
        }
    }
}
