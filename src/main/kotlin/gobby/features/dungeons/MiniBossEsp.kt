package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.features.render.ColoredEntityHighlighter
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.SelectorSetting
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import java.awt.Color

object MiniBossEsp : ColoredEntityHighlighter(
    "Mini Boss ESP", "Highlights mini bosses in dungeons", Category.DUNGEONS,
    Color(255, 170, 0, 72), "mini boss", "mini bosses"
) {


    private val MINIBOSS_NAMES = setOf(
        "Lost Adventurer",
        "Shadow Assassin",
        "Frozen Adventurer",
        "Angry Archaeologist",
        "King Midas"
    )

    fun matchesMiniBossName(name: String): Boolean = MINIBOSS_NAMES.any { name.contains(it) }

    fun isMiniBoss(entity: Entity): Boolean {
        if (entity is ArmorStand) return entity.customName?.string?.contains("Angry Archaeologist") == true
        if (entity !is Player || entity == mc.player) return false
        if (entity.isRemoved || entity.isSleeping) return false
        return matchesMiniBossName(entity.name.string) || matchesMiniBossName(entity.customName?.string ?: "")
    }

    override fun shouldHighlight(entity: Entity): Boolean {
        if (!inDungeons || inBoss) return false
        return isMiniBoss(entity)
    }

    override fun resolveEntity(entity: Entity): Entity? {
        if (entity is ArmorStand) {
            return getCorrespondingMob(entity)
        }
        return entity
    }





}
