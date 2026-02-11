package gobby.features.render

import gobby.config.GobbyConfig
import gobby.events.core.SubscribeEvent
import gobby.events.render.GammaEvent
import gobby.utils.ChatUtils.modMessage

object FullBright {

    @SubscribeEvent
    fun onGamma(event: GammaEvent) {
        if (GobbyConfig.fullBright) event.gamma = 15f
    }
}