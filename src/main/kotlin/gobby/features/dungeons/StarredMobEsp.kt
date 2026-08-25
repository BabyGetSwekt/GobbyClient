package gobby.features.dungeons

import gobby.features.render.ColoredEntityHighlighter
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ColorSetting
import gobby.gui.click.SelectorSetting
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import net.minecraft.world.entity.Entity
import java.awt.Color

object StarredMobEsp : ColoredEntityHighlighter(
    "Starred Mob ESP", "Highlights starred mobs in dungeons", Category.DUNGEONS,
    Color(255, 255, 239, 72), "starred mob", "starred mobs"
) {


    fun shouldHideLayers(entity: Entity): Boolean = isHighlighting(entity)

    private const val STAR = "\u272F"

    override fun shouldHighlight(entity: Entity): Boolean {
        if (!inDungeons || inBoss) return false
        val name = entity.customName?.string ?: return false
        if (!name.contains(STAR)) return false
        return !MiniBossEsp.matchesMiniBossName(name)
    }

    override fun shouldCacheMob(mob: Entity): Boolean = !MiniBossEsp.isMiniBoss(mob)

    override fun usesMobCaching(): Boolean = true





}
