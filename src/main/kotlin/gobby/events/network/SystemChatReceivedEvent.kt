package gobby.events.network

import gobby.events.Events
import net.minecraft.network.chat.Component

class SystemChatReceivedEvent(val message: String, val content: Component, val overlay: Boolean) : Events.Cancelable<Unit>()
