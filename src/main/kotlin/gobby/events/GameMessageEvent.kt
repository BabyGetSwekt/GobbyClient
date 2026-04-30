package gobby.events

class GameMessageEvent(val message: String) : Events.Cancelable<Unit>()
