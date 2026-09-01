package gobby.utils.managers

import gobby.events.core.SubscribeEvent
import gobby.events.gui.GuiOpenEvent
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen

interface SilentContainer {

    val isRunning: Boolean

    fun yieldToScreen()
}

object SilentContainerFlow {

    private val flows = mutableListOf<SilentContainer>()

    fun register(flow: SilentContainer) {
        flows += flow
    }

    @SubscribeEvent
    fun onGuiOpen(event: GuiOpenEvent) {
        if (event.screen !is AbstractContainerScreen<*>) return
        flows.filter { it.isRunning }.forEach { it.yieldToScreen() }
    }
}
