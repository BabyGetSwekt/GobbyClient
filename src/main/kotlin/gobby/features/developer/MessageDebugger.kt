package gobby.features.developer

import gobby.events.ChatReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Style
import net.minecraft.text.Text

object MessageDebugger : Module("Message Debugger", "Prints every chat message received (click to copy)", Category.DEVELOPER) {

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled || event.message.contains("Gobby Client")) return
        modMessage(Text.literal(event.message).setStyle(Style.EMPTY
            .withClickEvent(ClickEvent.CopyToClipboard(event.message))
            .withHoverEvent(HoverEvent.ShowText(Text.literal("§eClick to copy")))
        ))
    }
}
