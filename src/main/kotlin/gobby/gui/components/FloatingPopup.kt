package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.dsl.*
import java.awt.Color

private const val FULLY_TRANSPARENT = 0

class FloatingPopup(
    private val window: UIComponent,
    anchor: UIComponent,
    popupWidth: Float,
    popupHeight: Float,
    private val onClose: () -> Unit
) {
    private val catcher = UIBlock(Color(0, 0, 0, FULLY_TRANSPARENT)).constrain {
        width = 100.percent
        height = 100.percent
    } childOf window

    val container: UIRoundedRectangle

    init {
        val anchorBottom = anchor.getBottom()
        val yPosition = if (anchorBottom + popupHeight <= window.getHeight()) anchorBottom
                        else (anchor.getTop() - popupHeight).coerceAtLeast(0f)

        val anchorLeft = anchor.getLeft()
        val xPosition = if (anchorLeft + popupWidth <= window.getWidth()) anchorLeft
                        else (window.getWidth() - popupWidth).coerceAtLeast(0f)

        container = UIRoundedRectangle(ComponentTheme.CORNER_RADIUS).constrain {
            x = xPosition.pixels
            y = yPosition.pixels
            width = popupWidth.pixels
            height = popupHeight.pixels
            color = ComponentTheme.POPUP_BG.toConstraint()
        } childOf window

        catcher.onMouseClick { event ->
            event.stopPropagation()
            close()
        }
    }

    fun close() {
        window.removeChild(container)
        window.removeChild(catcher)
        onClose()
    }
}
