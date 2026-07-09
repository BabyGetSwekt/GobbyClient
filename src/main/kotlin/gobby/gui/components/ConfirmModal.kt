package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.font.FontProvider

private const val CORNER = 5f
private const val BOX_WIDTH = 240f
private const val BOX_HEIGHT = 82f
private const val BUTTON_WIDTH = 92f
private const val BUTTON_HEIGHT = 20f
private const val BUTTON_OFFSET = 56f
private const val BUTTON_BOTTOM = 12f
private const val MESSAGE_TOP = 16f
private const val MESSAGE_SCALE = 0.9f

class ConfirmModal(
    window: UIComponent,
    message: String,
    confirmText: String,
    cancelText: String,
    font: FontProvider? = null,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit
) : UIBlock(ComponentTheme.DIALOG_OVERLAY) {

    private val box = UIRoundedRectangle(CORNER).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        width = BOX_WIDTH.pixels
        height = BOX_HEIGHT.pixels
        color = ComponentTheme.DIALOG_BG.toConstraint()
    } childOf this

    init {
        constrain {
            width = 100.percent
            height = 100.percent
        }

        UIText(message, shadow = true).constrain {
            x = CenterConstraint()
            y = MESSAGE_TOP.pixels
            color = ComponentTheme.TEXT.toConstraint()
            textScale = MESSAGE_SCALE.pixels
            font?.let { fontProvider = it }
        } childOf box

        GobbyButton(confirmText, ComponentTheme.DANGER, ComponentTheme.DANGER_HOVER, font = font) { onConfirm() }.constrain {
            x = CenterConstraint() - BUTTON_OFFSET.pixels
            y = BUTTON_BOTTOM.pixels(alignOpposite = true)
            width = BUTTON_WIDTH.pixels
            height = BUTTON_HEIGHT.pixels
        } childOf box

        GobbyButton(cancelText, ComponentTheme.ACCENT_OFF, ComponentTheme.ROW_BG_HOVER, font = font) { onCancel() }.constrain {
            x = CenterConstraint() + BUTTON_OFFSET.pixels
            y = BUTTON_BOTTOM.pixels(alignOpposite = true)
            width = BUTTON_WIDTH.pixels
            height = BUTTON_HEIGHT.pixels
        } childOf box

        onMouseClick { it.stopPropagation() }

        this childOf window
        hide(instantly = true)
    }

    fun show() {
        unhide()
    }

    fun dismiss() {
        hide(instantly = true)
    }
}
