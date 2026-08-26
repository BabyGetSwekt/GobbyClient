package gobby.features.skyblock

import gobby.gui.click.AlwaysEnabled
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.screen.modhider.openModIdList

@AlwaysEnabled
object ModIdHiderModule : Module("Mod ID Hider", "Hide certain mod IDs from other mods", Category.COMMANDS, hasToggle = false) {
    init {
        onLeftClick = { openModIdList() }
    }
}
