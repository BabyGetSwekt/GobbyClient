package gobby.events

class CharTypedEvent(val key: Int) : Events.Cancelable<Unit>()