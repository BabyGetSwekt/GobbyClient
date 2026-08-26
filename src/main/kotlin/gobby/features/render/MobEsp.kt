package gobby.features.render

import gobby.gui.click.AlwaysEnabled
import gobby.gui.click.Category
import gobby.gui.screen.mobesp.openMobEspList
import net.minecraft.world.entity.Entity
import java.awt.Color

@AlwaysEnabled
object MobEsp : EntityHighlighter("Mob ESP", "Highlights mobs whose name matches your configured list", Category.COMMANDS) {

    init {
        onLeftClick = { openMobEspList() }
    }

    override fun shouldHighlight(entity: Entity): Boolean {
        if (!MobHighlighterConfig.hasActiveEntries()) return false
        val name = entity.customName?.string ?: return false
        return MobHighlighterConfig.matches(name)
    }

    override fun getColor(): Color = DEFAULT_MOB_COLOR

    override fun getColorFor(entity: Entity): Color {
        val name = entity.customName?.string ?: return DEFAULT_MOB_COLOR
        return MobHighlighterConfig.colorFor(name)?.let { Color(it, true) } ?: DEFAULT_MOB_COLOR
    }

    override fun usesMobCaching(): Boolean = true

    override fun revalidateCache(): Boolean = true

    override fun rendersArmor(): Boolean = true
}
