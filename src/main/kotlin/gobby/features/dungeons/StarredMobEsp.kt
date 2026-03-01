package gobby.features.dungeons

import gobby.features.render.EntityHighlighter
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.entity.Entity
import java.awt.Color

object StarredMobEsp : EntityHighlighter("Starred Mob ESP", "Highlights starred mobs in dungeons", Category.DUNGEONS) {

    val espColor by ColorSetting("ESP Color", Color(255, 255, 239, 72), desc = "Pick a color for starred mob highlights")
    val espLines by BooleanSetting("ESP Line", false, desc = "Draws a line to starred mobs")
    val espLineMode by SelectorSetting("Line Mode", 1, listOf("Feet", "Crosshair"), desc = "Where the line starts from")
        .withDependency { espLines }

    private const val STAR = "\u272F"
    private val MINIBOSS = setOf(
        "Angry Archaeologist",
        "Frozen Adventurer",
        "Lost Adventurer",
        "Shadow Assassin",
        "King Midas"
    )

    override fun shouldHighlight(entity: Entity): Boolean {
        if (!inDungeons || inBoss) return false
        val name = entity.customName?.string ?: return false
        if (!name.contains(STAR)) return false
        return MINIBOSS.none { name.contains(it) }
    }

    override fun usesMobCaching(): Boolean = true
    override fun getColor(): Color = espColor
    override fun shouldDrawLines(): Boolean = espLines
    override fun getLineColor(): Color = espColor
    override fun getLineMode(): Int = espLineMode
}
