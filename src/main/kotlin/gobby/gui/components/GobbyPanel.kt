package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.font.FontProvider
import java.awt.Color

private const val NO_BOTTOM_BAR = 0f

class GobbyPanel(
    window: UIComponent,
    title: String,
    private val font: FontProvider? = null,
    closeButton: Boolean = true,
    bottomBarHeight: Float = NO_BOTTOM_BAR,
    private val onDismiss: () -> Unit
) : UIRoundedRectangle(ComponentTheme.PANEL_CORNER) {

    val titleBar = UIBlock(ComponentTheme.TITLE_BAR_BG).constrain {
        width = 100.percent
        height = ComponentTheme.TITLE_BAR_HEIGHT.pixels
    } childOf this

    val bottomBar: UIBlock? = bottomBarHeight.takeIf { it > NO_BOTTOM_BAR }?.let { height ->
        UIBlock(ComponentTheme.TITLE_BAR_BG).constrain {
            y = 0.pixels(alignOpposite = true)
            width = 100.percent
            this.height = height.pixels
        } childOf this
    }

    init {
        attachOverlay(window)
        this childOf window
        constrain {
            x = CenterConstraint()
            y = CenterConstraint()
            color = ComponentTheme.PANEL_BG.toConstraint()
        }
        if (closeButton) addCloseButton()
        addTitle(title, closeButton)
    }

    fun <T : UIComponent> contentArea(component: T, top: Float, bottomReserved: Float): T = component.constrain {
        x = ComponentTheme.SIDE_PAD.pixels
        y = top.pixels
        width = 100.percent - (ComponentTheme.SIDE_PAD * 2).pixels
        height = 100.percent - bottomReserved.pixels
    } childOf this

    fun label(text: String, scale: Float = ComponentTheme.LABEL_SCALE, textColor: Color = ComponentTheme.TEXT): UIText =
        UIText(text, shadow = true).constrain {
            color = textColor.toConstraint()
            textScale = scale.pixels
            font?.let { fontProvider = it }
        }

    private fun attachOverlay(window: UIComponent) {
        UIBlock(ComponentTheme.OVERLAY).constrain {
            width = 100.percent
            height = 100.percent
        }.also { overlay ->
            overlay childOf window
            overlay.onMouseClick { onDismiss() }
        }
    }

    private fun addCloseButton() {
        GobbyCloseButton { onDismiss() }.constrain {
            x = ComponentTheme.SIDE_PAD.pixels
            y = CenterConstraint()
            width = ComponentTheme.ICON_SIZE.pixels
            height = ComponentTheme.ICON_SIZE.pixels
        } childOf titleBar
    }

    private fun addTitle(title: String, closeButton: Boolean) {
        val leading = if (closeButton) ComponentTheme.SIDE_PAD + ComponentTheme.ICON_SIZE + ComponentTheme.TITLE_GAP
        else ComponentTheme.SIDE_PAD
        UIText(title, shadow = true).constrain {
            x = leading.pixels
            y = CenterConstraint()
            color = ComponentTheme.TEXT.toConstraint()
            font?.let { fontProvider = it }
        } childOf titleBar
    }
}
