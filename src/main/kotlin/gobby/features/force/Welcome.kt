package gobby.features.force

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.config.GobbyConfig.markDirty
import gobby.events.core.SubscribeEvent
import gobby.events.network.ClientConnectedToServerEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.onHypixel
import gobby.utils.timer.Executor

object Welcome {

    private var scheduled = false

    @SubscribeEvent
    fun onServerJoin(event: ClientConnectedToServerEvent) {
        if (mc.player == null || mc.world == null || GobbyConfig.welcome || scheduled) return
        scheduled = true
        Executor.execute(80) {
            if (!onHypixel) return@execute
            modMessage("Hello! Use §e/gobby help §7to see available commands.")
            modMessage("For bugs/suggestions, dm @Goblinbanaan on discord")
            GobbyConfig.welcome = true
            markDirty()
        }
    }
}