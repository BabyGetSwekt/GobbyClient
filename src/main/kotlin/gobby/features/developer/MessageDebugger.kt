package gobby.features.developer

import gobby.events.ChatReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.Component

object MessageDebugger : Module("Message Debugger", "Prints every chat message received (click to copy)", Category.DEVELOPER) {

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled || event.message.contains("Gobby Client")) return
        modMessage(Component.literal(event.message).setStyle(Style.EMPTY
            .withClickEvent(ClickEvent.CopyToClipboard(event.message))
            .withHoverEvent(HoverEvent.ShowText(Component.literal("§eClick to copy")))
        ))
    }
}
