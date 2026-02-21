package gobby.features.dungeons

import gobby.config.GobbyConfig
import gobby.features.render.EntityHighlighter
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.entity.Entity
import java.awt.Color

object StarredMobEsp : EntityHighlighter() {

    private const val STAR = "✯"
    private val MINIBOSS = setOf(
        "Angry Archaeologist",
        "Frozen Adventurer",
        "Lost Adventurer",
        "Shadow Assassin",
        "King Midas"
    )

    override fun isEnabled(): Boolean = GobbyConfig.starredMobEsp

    override fun shouldHighlight(entity: Entity): Boolean {
        if (!inDungeons || inBoss) return false
        val name = entity.customName?.string ?: return false
        if (!name.contains(STAR)) return false
        return MINIBOSS.none { name.contains(it) }
    }

    override fun usesMobCaching(): Boolean = true
    override fun getColor(): Color = GobbyConfig.starredMobEspColor
    override fun shouldDrawLines(): Boolean = GobbyConfig.starredMobEspLines
    override fun getLineColor(): Color = GobbyConfig.starredMobEspColor
    override fun getLineMode(): Int = GobbyConfig.starredMobEspLineMode
}
