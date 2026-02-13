package gobby.features.dungeons

import gobby.config.GobbyConfig
import gobby.features.render.EntityHighlighter
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.entity.Entity
import java.awt.Color

object MiniBossEsp : EntityHighlighter() {

    private const val STAR = "✯"
    private val MINIBOSS_NAMES = setOf(
        "Angry Archaeologist",
        "Frozen Adventurer",
        "Lost Adventurer"
    )

    override fun isEnabled(): Boolean = GobbyConfig.miniBossEsp

    override fun shouldHighlight(entity: Entity): Boolean {
        if (!inDungeons || inBoss) return false
        val name = entity.customName?.string ?: return false
        if (!name.contains(STAR)) return false
        return MINIBOSS_NAMES.any { name.contains(it) }
    }

    override fun usesMobCaching(): Boolean = true
    override fun getColor(): Color = GobbyConfig.miniBossEspColor
    override fun shouldDrawLines(): Boolean = GobbyConfig.miniBossEspLines
    override fun getLineColor(): Color = GobbyConfig.miniBossEspColor
    override fun getLineMode(): Int = GobbyConfig.miniBossEspLineMode
}
