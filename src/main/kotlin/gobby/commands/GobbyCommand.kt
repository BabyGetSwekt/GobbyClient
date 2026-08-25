package gobby.commands

import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent

object GobbyCommand {
    @SubscribeEvent
    fun register(event: CommandRegisterEvent) = GobbyCommandTree.register(event)
}
