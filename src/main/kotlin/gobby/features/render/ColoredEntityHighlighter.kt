package gobby.features.render

import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.ModelPreviewSetting
import gobby.gui.click.SelectorSetting
import gobby.gui.click.SettingSection
import gobby.gui.click.inGroup
import java.awt.Color

private const val DEFAULT_LINE_MODE = 1
private val PREVIEW_SECTION = SettingSection("Model Preview")

abstract class ColoredEntityHighlighter(
    name: String,
    description: String,
    category: Category,
    defaultColor: Color,
    subject: String,
    subjectPlural: String
) : EntityHighlighter(name, description, category) {

    private val colorSetting = ColorSetting("ESP Color", defaultColor, desc = "Pick a color for $subject highlights")

    val espColor by colorSetting
    val espLines by BooleanSetting("ESP Line", false, desc = "Draws a line to $subjectPlural")
    val espLineMode by SelectorSetting("Line Mode", DEFAULT_LINE_MODE, listOf("Feet", "Crosshair"), desc = "Where the line starts from")
        .withDependency { espLines }

    val espPreview by ModelPreviewSetting("Model Preview", colorSetting).inGroup(PREVIEW_SECTION)

    override fun getColor(): Color = espColor

    override fun shouldDrawLines(): Boolean = espLines

    override fun getLineColor(): Color = espColor

    override fun getLineMode(): Int = espLineMode

    override fun rendersArmor(): Boolean = true
}
