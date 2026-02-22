package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.features.render.EntityHighlighter
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.player.PlayerEntity
import java.awt.Color

object MiniBossEsp : EntityHighlighter() {

    private val MINIBOSS_NAMES = setOf(
        "Lost Adventurer",
        "Shadow Assassin",
        "Frozen Adventurer",
        "Angry Archaeologist",
        "King Midas"
    )

    override fun isEnabled(): Boolean = GobbyConfig.miniBossEsp

    override fun shouldHighlight(entity: Entity): Boolean {
        if (!inDungeons || inBoss) return false

        if (entity is ArmorStandEntity) {
            val customName = entity.customName?.string ?: ""
            if (customName.contains("Angry Archaeologist")) return true
        }

        if (entity !is PlayerEntity) return false
        if (entity == mc.player) return false
        if (entity.isDead || entity.isSleeping) return false
        val name = entity.name.string
        val customName = entity.customName?.string ?: ""
        return MINIBOSS_NAMES.any { name.contains(it) || customName.contains(it) }
    }

    override fun resolveEntity(entity: Entity): Entity? {
        if (entity is ArmorStandEntity) {
            return getCorrespondingMob(entity)
        }
        return entity
    }

    override fun getColor(): Color = GobbyConfig.miniBossEspColor
    override fun shouldDrawLines(): Boolean = GobbyConfig.miniBossEspLines
    override fun getLineColor(): Color = GobbyConfig.miniBossEspColor
    override fun getLineMode(): Int = GobbyConfig.miniBossEspLineMode
}
