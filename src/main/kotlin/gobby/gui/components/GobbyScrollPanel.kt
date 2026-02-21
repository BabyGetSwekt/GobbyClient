package gobby.gui.components

import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import java.awt.Color

/**
 * Reusable scroll panel with a styled scroll track and grip.
 * Add children to [scrollArea] to populate the scrollable content.
 */
class GobbyScrollPanel(
    emptyString: String = "",
    innerPadding: Float = 4f,
    pixelsPerScroll: Float = 40f,
    scrollAcceleration: Float = 1.8f
) : UIBlock(Color(0, 0, 0, 0)) {

    val scrollArea by ScrollComponent(
        emptyString = emptyString,
        innerPadding = innerPadding,
        pixelsPerScroll = pixelsPerScroll,
        scrollAcceleration = scrollAcceleration
    ).constrain {
        width = 100.percent - 14.pixels
        height = 100.percent
    } childOf this

    private val scrollTrack by UIRoundedRectangle(3f).constrain {
        x = 0.pixels(alignOpposite = true)
        width = 8.pixels
        height = 100.percent
        color = Color(12, 12, 16, 180).toConstraint()
    } childOf this

    private val scrollGrip by UIRoundedRectangle(3f).constrain {
        x = CenterConstraint()
        width = 6.pixels
        color = Color(70, 70, 80, 200).toConstraint()
    } childOf scrollTrack

    init {
        scrollArea.setVerticalScrollBarComponent(scrollGrip, hideWhenUseless = true)

        scrollGrip.onMouseEnter {
            scrollGrip.animate {
                setColorAnimation(Animations.OUT_EXP, 0.15f, Color(110, 110, 130, 240).toConstraint())
            }
        }
        scrollGrip.onMouseLeave {
            scrollGrip.animate {
                setColorAnimation(Animations.OUT_EXP, 0.2f, Color(70, 70, 80, 200).toConstraint())
            }
        }
    }
}
