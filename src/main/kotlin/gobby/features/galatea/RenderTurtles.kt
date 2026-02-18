package gobby.features.galatea

import gobby.config.GobbyConfig
import gobby.features.render.EntityHighlighter
import net.minecraft.entity.Entity
import net.minecraft.entity.passive.TurtleEntity
import java.awt.Color

object RenderTurtles : EntityHighlighter() {

    override fun isEnabled(): Boolean = GobbyConfig.turtleEsp

    override fun shouldHighlight(entity: Entity): Boolean = entity is TurtleEntity

    override fun usesMobCaching(): Boolean = true
    override fun getColor(): Color = GobbyConfig.turtleEspColor
    override fun shouldDrawLines(): Boolean = GobbyConfig.turtleEspLines
    override fun getLineColor(): Color = GobbyConfig.turtleEspLineColor
    override fun getLineMode(): Int = GobbyConfig.turtleEspLineMode
}
