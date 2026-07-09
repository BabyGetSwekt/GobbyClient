package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.elementa.font.FontProvider

private const val OPTION_HEIGHT = 16f
private const val LABEL_PADDING = 5f
private const val CARET_PADDING = 6f
private const val CHEVRON_WIDTH = 7f
private const val CHEVRON_HEIGHT = 4f

class GobbyDropdown(
    private val window: UIComponent,
    private val options: List<String>,
    initialIndex: Int,
    private val font: FontProvider? = null,
    private val onSelect: (Int) -> Unit
) : UIRoundedRectangle(ComponentTheme.CORNER_RADIUS) {

    var selectedIndex = initialIndex.coerceIn(0, options.lastIndex)
        private set

    private var popup: FloatingPopup? = null

    private val label = UIText(options[selectedIndex], shadow = true).constrain {
        x = LABEL_PADDING.pixels
        y = CenterConstraint()
        color = ComponentTheme.TEXT.toConstraint()
        font?.let { fontProvider = it }
    } childOf this

    private val caret = GobbyChevron().constrain {
        x = CARET_PADDING.pixels(alignOpposite = true)
        y = CenterConstraint()
        width = CHEVRON_WIDTH.pixels
        height = CHEVRON_HEIGHT.pixels
    } childOf this

    init {
        constrain { color = ComponentTheme.INPUT_BG.toConstraint() }
        enableEffect(OutlineEffect(ComponentTheme.BORDER, 1f))
        applyHoverColor(ComponentTheme.INPUT_BG, ComponentTheme.ROW_BG_HOVER)
        onMouseClick { event ->
            event.stopPropagation()
            if (popup == null) openPopup() else popup?.close()
        }
    }

    private fun openPopup() {
        val newPopup = FloatingPopup(window, this, getWidth(), options.size * OPTION_HEIGHT) { popup = null }
        buildOptions(newPopup)
        popup = newPopup
    }

    private fun buildOptions(host: FloatingPopup) {
        options.forEachIndexed { index, option ->
            val row = UIBlock(ComponentTheme.POPUP_BG).constrain {
                y = (index * OPTION_HEIGHT).pixels
                width = 100.percent
                height = OPTION_HEIGHT.pixels
            } childOf host.container

            UIText(option, shadow = true).constrain {
                x = LABEL_PADDING.pixels
                y = CenterConstraint()
                color = (if (index == selectedIndex) ComponentTheme.ACCENT else ComponentTheme.TEXT).toConstraint()
                font?.let { fontProvider = it }
            } childOf row

            row.applyHoverColor(ComponentTheme.POPUP_BG, ComponentTheme.ROW_BG_HOVER)
            row.onMouseClick { event ->
                event.stopPropagation()
                select(index, host)
            }
        }
    }

    private fun select(index: Int, host: FloatingPopup) {
        selectedIndex = index
        label.setText(options[index])
        host.close()
        onSelect(index)
    }
}
