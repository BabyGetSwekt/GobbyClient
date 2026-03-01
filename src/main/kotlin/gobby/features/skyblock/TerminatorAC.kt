package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.PlayerUtils.leftClick
import gobby.utils.isHolding

object TerminatorAC : Module("Terminator Autoclick", "Automatically left clicks for the salvation ability", Category.SKYBLOCK) {

    val cps by NumberSetting("Clicks Per Second", 5, 1, 12, desc = "The amount of clicks per second")

    private var nextClickTime = 0.0

    @SubscribeEvent
    fun onRenderWorld(event: NewRender3DEvent) {
        val player = mc.player ?: return
        if (mc.world == null || mc.currentScreen != null || !enabled) return
        if (!player.mainHandStack.isHolding("TERMINATOR") || !mc.options.useKey.isPressed) return

        val currentTime = System.currentTimeMillis()
        if (currentTime < nextClickTime) return

        leftClick()
        nextClickTime = currentTime + calculateNextClickDelay()
    }

    private fun calculateNextClickDelay(): Double {
        val baseDelay = 1000.0 / cps
        val randomOffset = (Math.random() - 0.5) * 60.0
        return baseDelay + randomOffset
    }
}
