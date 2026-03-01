package gobby.features.force

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.network.ClientConnectedToServerEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.ConfigManager
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.onHypixel
import gobby.utils.timer.Executor

object Welcome : Module("Welcome", category = Category.SKYBLOCK, hidden = true) {

    var welcomed by BooleanSetting("welcomed", false, hidden = true)

    private var scheduled = false

    @SubscribeEvent
    fun onServerJoin(event: ClientConnectedToServerEvent) {
        if (mc.player == null || mc.world == null || welcomed || scheduled) return
        scheduled = true
        Executor.execute(80) {
            if (!onHypixel) return@execute
            modMessage("Hello! Use §e/gobby help §7to see available commands.")
            modMessage("For bugs/suggestions, dm @Goblinbanaan on discord")
            welcomed = true
            ConfigManager.save()
        }
    }
}
