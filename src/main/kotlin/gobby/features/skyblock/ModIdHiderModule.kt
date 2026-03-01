package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.gui.ModIdHiderScreen
import gobby.gui.click.ActionSetting
import gobby.gui.click.AlwaysEnabled
import gobby.gui.click.Category
import gobby.gui.click.Module

@AlwaysEnabled
object ModIdHiderModule : Module("Mod ID Hider", "Hide certain mod IDs from other mods", Category.SKYBLOCK, hasToggle = false) {
    val open by ActionSetting("Open", desc = "Opens the Mod ID Hider screen (can also be opened with /gobby modid)") {
        mc.send { ModIdHiderScreen.open() }
    }
}
