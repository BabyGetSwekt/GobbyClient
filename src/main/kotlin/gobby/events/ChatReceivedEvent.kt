package gobby.events

class ChatReceivedEvent(val message: String) : Events.Cancelable<Unit>()