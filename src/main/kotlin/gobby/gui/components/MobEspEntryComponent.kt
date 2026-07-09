package gobby.gui.components

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.font.FontProvider
import gobby.features.render.MobEntry
import gobby.features.render.MobFilter

object MobEspColumns {
    const val ROW_HEIGHT = 26f
    const val SIDE_PAD = 5f
    const val GAP = 5f
    const val CHECKBOX_SIZE = 16f
    const val DROPDOWN_WIDTH = 74f
    const val COLOR_WIDTH = 20f
    const val REMOVE_SIZE = 16f
    const val INNER_HEIGHT = ROW_HEIGHT - GAP * 2
    const val NAME_X = SIDE_PAD + CHECKBOX_SIZE + GAP
    const val NAME_RESERVED = NAME_X + DROPDOWN_WIDTH + GAP + COLOR_WIDTH + GAP + REMOVE_SIZE + SIDE_PAD
    const val DROPDOWN_FROM_RIGHT = SIDE_PAD + REMOVE_SIZE + GAP + COLOR_WIDTH + GAP
    const val DROPDOWN_LEFT_FROM_RIGHT = DROPDOWN_FROM_RIGHT + DROPDOWN_WIDTH
}

private val FILTER_OPTIONS = MobFilter.entries.map { it.label }

class MobEspEntryComponent(
    window: UIComponent,
    entry: MobEntry,
    font: FontProvider? = null,
    onChange: () -> Unit,
    onRemove: (MobEspEntryComponent) -> Unit
) : UIRoundedRectangle(ComponentTheme.CORNER_RADIUS) {

    private val checkbox = GobbyCheckbox(entry.enabled) { onChange() }.constrain {
        x = MobEspColumns.SIDE_PAD.pixels
        y = CenterConstraint()
        width = MobEspColumns.CHECKBOX_SIZE.pixels
        height = MobEspColumns.CHECKBOX_SIZE.pixels
    } childOf this

    private val nameField = GobbyTextField(entry.name, "Mob name...") { onChange() }.constrain {
        x = MobEspColumns.NAME_X.pixels
        y = CenterConstraint()
        width = 100.percent - MobEspColumns.NAME_RESERVED.pixels
        height = MobEspColumns.INNER_HEIGHT.pixels
    } childOf this

    private val dropdown = GobbyDropdown(window, FILTER_OPTIONS, entry.filter.ordinal, font = font) { onChange() }.constrain {
        x = MobEspColumns.DROPDOWN_FROM_RIGHT.pixels(alignOpposite = true)
        y = CenterConstraint()
        width = MobEspColumns.DROPDOWN_WIDTH.pixels
        height = MobEspColumns.INNER_HEIGHT.pixels
    } childOf this

    private val colorPicker = GobbyColorPicker(window, entry.awtColor) { onChange() }.constrain {
        x = (MobEspColumns.SIDE_PAD + MobEspColumns.REMOVE_SIZE + MobEspColumns.GAP).pixels(alignOpposite = true)
        y = CenterConstraint()
        width = MobEspColumns.COLOR_WIDTH.pixels
        height = MobEspColumns.INNER_HEIGHT.pixels
    } childOf this

    init {
        constrain {
            width = 100.percent
            height = MobEspColumns.ROW_HEIGHT.pixels
            color = ComponentTheme.ROW_BG.toConstraint()
        }

        GobbyCloseButton { onRemove(this@MobEspEntryComponent) }.constrain {
            x = MobEspColumns.SIDE_PAD.pixels(alignOpposite = true)
            y = CenterConstraint()
            width = MobEspColumns.REMOVE_SIZE.pixels
            height = MobEspColumns.REMOVE_SIZE.pixels
        } childOf this
    }

    fun toEntry(): MobEntry = MobEntry(
        enabled = checkbox.checked,
        name = nameField.getText().trim(),
        filter = MobFilter.entries[dropdown.selectedIndex],
        color = colorPicker.getColorValue().rgb
    )
}
