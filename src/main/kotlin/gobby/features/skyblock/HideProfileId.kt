package gobby.features.skyblock

import gobby.events.core.SubscribeEvent
import gobby.events.ChatReceivedEvent
import gobby.gui.click.Category
import gobby.gui.click.Module

object HideProfileId : Module(
    "Hide Profile ID",
    "Prevents Profile ID to show up in chat",
    Category.SKYBLOCK
) {

    private val PROFILE_ID = Regex("""^Profile ID: [0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}""")

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        if (!enabled) return
        if (PROFILE_ID.containsMatchIn(event.message.trim())) event.cancel()
    }
}
