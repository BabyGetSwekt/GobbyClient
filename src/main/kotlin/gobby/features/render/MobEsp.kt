package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.gui.MobEspScreen
import gobby.gui.click.ActionSetting
import gobby.gui.click.AlwaysEnabled
import gobby.gui.click.Category
import net.minecraft.world.entity.Entity
import java.awt.Color

@AlwaysEnabled
object MobEsp : EntityHighlighter("Mob ESP", "Highlights mobs whose name matches your configured list", Category.RENDER) {

    val open by ActionSetting("Open", desc = "Opens the Mob ESP list (click the module or /gobby mobesp)") {
        mc.execute { MobEspScreen.open() }
    }

    init {
        onLeftClick = { mc.execute { MobEspScreen.open() } }
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

    override fun rendersArmor(): Boolean = true
}
