package gobby.events.network

import gobby.events.Events
import net.minecraft.text.Text

class SystemChatReceivedEvent(val message: String, val content: Text, val overlay: Boolean) : Events.Cancelable<Unit>()
