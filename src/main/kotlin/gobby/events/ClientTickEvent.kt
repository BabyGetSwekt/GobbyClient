package gobby.events

sealed class ClientTickEvent : Events() {
    object Pre : ClientTickEvent()
    object Post : ClientTickEvent()
}