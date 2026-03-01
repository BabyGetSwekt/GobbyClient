package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.features.render.EntityHighlighter
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.Module
import gobby.gui.click.SelectorSetting
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.player.PlayerEntity
import java.awt.Color

object MiniBossEsp : EntityHighlighter("Mini Boss ESP", "Highlights mini bosses in dungeons", Category.DUNGEONS) {

    val espColor by ColorSetting("ESP Color", Color(255, 170, 0, 72), desc = "Pick a color for mini boss highlights")
    val espLines by BooleanSetting("ESP Line", false, desc = "Draws a line to mini bosses")
    val espLineMode by SelectorSetting("Line Mode", 1, listOf("Feet", "Crosshair"), desc = "Where the line starts from")
        .withDependency { espLines }

    private val MINIBOSS_NAMES = setOf(
        "Lost Adventurer",
        "Shadow Assassin",
        "Frozen Adventurer",
        "Angry Archaeologist",
        "King Midas"
    )

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

    override fun getColor(): Color = espColor
    override fun shouldDrawLines(): Boolean = espLines
    override fun getLineColor(): Color = espColor
    override fun getLineMode(): Int = espLineMode
}
