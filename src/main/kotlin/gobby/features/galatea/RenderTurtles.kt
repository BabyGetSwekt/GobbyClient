package gobby.features.galatea

import gobby.features.render.EntityHighlighter
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import net.minecraft.entity.Entity
import net.minecraft.entity.passive.TurtleEntity
import java.awt.Color

object RenderTurtles : EntityHighlighter("Turtle ESP", "Enable turtle ESP, used for shards", Category.GALATEA) {

    val espColor by ColorSetting("ESP Color", Color(208, 88, 2, 72), desc = "Pick a color")
    val espLines by BooleanSetting("ESP Line", true, desc = "Draws a line to the turtle")
    val espLineColor by ColorSetting("Line Color", Color(208, 88, 2, 100), desc = "Pick a color of the line")
    val espLineMode by SelectorSetting("Line Mode", 1, listOf("Feet", "Crosshair"), desc = "At which part you want the lines to be rendered")

    override fun shouldHighlight(entity: Entity): Boolean = entity is TurtleEntity

    override fun usesMobCaching(): Boolean = true
    override fun getColor(): Color = espColor
    override fun shouldDrawLines(): Boolean = espLines
    override fun getLineColor(): Color = espLineColor
    override fun getLineMode(): Int = espLineMode
}
