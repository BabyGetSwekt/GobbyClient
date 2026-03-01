package gobby.features.floor7

import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module

object P5DebuffHelper : Module(
    "P5 Debuff Helper", "P5 debuff helpers for Floor 7",
    Category.FLOOR7
) {
    val lastBreathHelper by BooleanSetting("Last Breath Helper", false, desc = "Automatically releases and re-charges LB under a dragon")
    val renderDebuffArea by BooleanSetting("Render Debuff Area", false, desc = "Renders the areas where debuff timing applies")
}
