package gobby.features.render

import gobby.events.core.SubscribeEvent
import gobby.events.render.GammaEvent
import gobby.gui.click.Category
import gobby.gui.click.Module

object FullBright : Module("Full Bright", "Enables full bright", Category.RENDER, defaultEnabled = true) {


    @SubscribeEvent
    fun onGamma(event: GammaEvent) {
        if (enabled) event.gamma = 15f
    }
}
