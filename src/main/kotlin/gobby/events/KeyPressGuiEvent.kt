package gobby.events

class KeyPressGuiEvent(val key: Int) : Events.Cancelable<Unit>()