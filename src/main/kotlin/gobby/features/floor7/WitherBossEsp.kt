package gobby.features.floor7

import gobby.features.render.EntityHighlighter
import gobby.features.render.EspStyle
import gobby.gui.click.Category
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.phys.AABB
import java.awt.Color

object WitherBossEsp : EntityHighlighter(
    "Wither Boss ESP", "Draws an ESP around Maxor, Storm, Goldor and Necron.",
    Category.FLOOR7
) {

    private const val FULL_RENDER_SCALE = 2f
    private const val INVULNERABLE_SHRINK_TICKS = 220f
    private const val INVULNERABLE_SHRINK = 0.5f
    private const val MIN_BOSS_RENDER_SCALE = 1f
    private const val SHOULDER_HALF_WIDTH = 10.0 / 16.0

    override fun espStyle(): EspStyle = EspStyle.CONNECTED

    override fun getColor(): Color = Color(0, 255, 0, 80)

    override fun shouldHighlight(entity: Entity): Boolean =
        entity is WitherBoss && !entity.isInvisible && renderScale(entity) >= MIN_BOSS_RENDER_SCALE

    override fun boxFor(entity: Entity): AABB {
        if (entity !is WitherBoss) return entity.boundingBox
        val box = entity.boundingBox
        val halfWidth = SHOULDER_HALF_WIDTH * renderScale(entity)
        return box.inflate((halfWidth - box.xsize / 2).coerceAtLeast(0.0), 0.0, (halfWidth - box.zsize / 2).coerceAtLeast(0.0))
    }

    private fun renderScale(wither: WitherBoss): Float {
        val ticks = wither.invulnerableTicks
        if (ticks <= 0) return FULL_RENDER_SCALE
        return FULL_RENDER_SCALE - ticks / INVULNERABLE_SHRINK_TICKS * INVULNERABLE_SHRINK
    }
}
