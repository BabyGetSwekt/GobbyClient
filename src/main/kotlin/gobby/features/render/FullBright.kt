package gobby.features.render

import gobby.config.GobbyConfig
import gobby.events.core.SubscribeEvent
import gobby.events.render.GammaEvent

object FullBright {

    @SubscribeEvent
    fun onGamma(event: GammaEvent) {
        if (GobbyConfig.fullBright) event.gamma = 15f
    }
}