package gobby.commands

import gobby.events.CommandRegisterEvent

internal object GobbyCommandTree {
    fun register(event: CommandRegisterEvent) {
        GobbyCommandBasic.register(event)
        GobbyCommandNavigation.register(event)
        GobbyCommandWorld.register(event)
        GobbyCommandStorage.register(event)
    }
}
