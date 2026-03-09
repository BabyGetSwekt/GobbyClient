package gobby.features.developer

import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module

object DevMode : Module("Developer Mode", "Enables extra features for debugging/developing GobbyClient", Category.DEVELOPER) {
    val enableDevMessages by BooleanSetting("Enable Dev Messages", false, desc = "Enables you to see debug messages")
    val forceDungeons by BooleanSetting("Force in Dungeons", false, desc = "Forces you to be in dungeons")
    val forceFloor7 by BooleanSetting("Force Floor 7", false, desc = "Forces you to be in floor 7").withDependency { forceDungeons }
}
