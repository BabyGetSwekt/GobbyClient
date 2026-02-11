package gobby.events

import net.minecraft.text.Text

class ChatReceivedEvent(val message: Text) : Events.Cancelable<Unit>()