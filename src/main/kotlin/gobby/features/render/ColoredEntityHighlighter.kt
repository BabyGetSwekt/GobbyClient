package gobby.features.render

import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.SelectorSetting
import java.awt.Color

private const val DEFAULT_LINE_MODE = 1

abstract class ColoredEntityHighlighter(
    name: String,
    description: String,
    category: Category,
    defaultColor: Color,
    subject: String,
    subjectPlural: String
) : EntityHighlighter(name, description, category) {

    val espColor by ColorSetting("ESP Color", defaultColor, desc = "Pick a color for $subject highlights")
    val espLines by BooleanSetting("ESP Line", false, desc = "Draws a line to $subjectPlural")
    val espLineMode by SelectorSetting("Line Mode", DEFAULT_LINE_MODE, listOf("Feet", "Crosshair"), desc = "Where the line starts from")
        .withDependency { espLines }

    override fun getColor(): Color = espColor

    override fun shouldDrawLines(): Boolean = espLines

    override fun getLineColor(): Color = espColor

    override fun getLineMode(): Int = espLineMode

    override fun rendersArmor(): Boolean = true
}
