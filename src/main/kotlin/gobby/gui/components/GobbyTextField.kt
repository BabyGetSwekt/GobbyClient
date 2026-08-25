package gobby.gui.components

import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.input.UITextInput
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.elementa.font.FontProvider

private const val SIDE_PADDING = 4f
private const val TEXT_HEIGHT = 10f
private const val ENTER = '\r'

class GobbyTextField(
    initial: String = "",
    placeholder: String,
    font: FontProvider? = null,
    private val onChange: (String) -> Unit = {},
    private val onSubmit: (String) -> Unit = {}
) : UIRoundedRectangle(ComponentTheme.CORNER_RADIUS) {

    private val input = UITextInput(placeholder).constrain {
        x = SIDE_PADDING.pixels
        y = CenterConstraint()
        width = 100.percent - (SIDE_PADDING * 2).pixels
        height = TEXT_HEIGHT.pixels
        color = ComponentTheme.TEXT.toConstraint()
        font?.let { fontProvider = it }
    } childOf this

    init {
        constrain { color = ComponentTheme.INPUT_BG.toConstraint() }
        enableEffect(OutlineEffect(ComponentTheme.BORDER, 1f))
        if (initial.isNotEmpty()) input.setText(initial)
        onMouseClick { input.grabWindowFocus() }
        input.onKeyType { typedChar, _ ->
            if (typedChar == ENTER) onSubmit(getText()) else onChange(getText())
        }
    }

    fun getText(): String = input.getText()

    fun clear() = input.setText("")

    fun focusInput() = input.grabWindowFocus()
}
