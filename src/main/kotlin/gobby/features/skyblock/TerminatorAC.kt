package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.PlayerUtils.leftClick
import gobby.utils.isHolding

object TerminatorAC {

    private var nextClickTime = 0.0

    @SubscribeEvent
    fun onRenderWorld(event: NewRender3DEvent) {
        val player = mc.player ?: return
        if (mc.world == null || mc.currentScreen != null || !GobbyConfig.terminatorAc) return
        if (!player.mainHandStack.isHolding("TERMINATOR") || !mc.options.useKey.isPressed) return

        val currentTime = System.currentTimeMillis()
        if (currentTime < nextClickTime) return

        leftClick()
        nextClickTime = currentTime + calculateNextClickDelay()
    }

    private fun calculateNextClickDelay(): Double {
        val baseDelay = 1000.0 / GobbyConfig.terminatorCps
        val randomOffset = (Math.random() - 0.5) * 60.0
        return baseDelay + randomOffset
    }
}
