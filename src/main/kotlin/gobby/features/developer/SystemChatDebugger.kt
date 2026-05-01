package gobby.features.developer

import gobby.events.core.SubscribeEvent
import gobby.events.network.SystemChatReceivedEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage

object SystemChatDebugger : Module("System Chat Debugger", "Prints every ClientboundSystemChatPacket received", Category.DEVELOPER) {

    @SubscribeEvent
    fun onSystemChat(event: SystemChatReceivedEvent) {
        if (!enabled) return
        modMessage(event.message)
    }
}
