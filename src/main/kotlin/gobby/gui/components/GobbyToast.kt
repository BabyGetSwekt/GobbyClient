package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.font.FontProvider
import gobby.utils.timer.Executor
import java.awt.Color

private const val WIDTH = 200f
private const val HEIGHT = 40f
private const val DEFAULT_TICKS = 40
private val TOAST_BG = Color(30, 10, 10, 245)
private val TOAST_TEXT = Color(255, 80, 80)

class GobbyToast(
    window: UIComponent,
    message: String,
    font: FontProvider? = null,
    private val visibleTicks: Int = DEFAULT_TICKS
) : UIRoundedRectangle(ComponentTheme.PANEL_CORNER) {

    private var pending: Executor.ScheduledTask? = null

    init {
        constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            width = WIDTH.pixels
            height = HEIGHT.pixels
            color = TOAST_BG.toConstraint()
        }
        UIText(message, shadow = true).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            color = TOAST_TEXT.toConstraint()
            textScale = ComponentTheme.LABEL_SCALE.pixels
            font?.let { fontProvider = it }
        } childOf this
        this childOf window
        hide(instantly = true)
    }

    fun show() {
        pending?.let(Executor::cancel)
        unhide()
        pending = Executor.schedule(visibleTicks) {
            hide()
            pending = null
        }
    }
}
