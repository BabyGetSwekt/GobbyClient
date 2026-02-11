package gobby.events

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

class CommandRegisterEvent(private val dispatcher: CommandDispatcher<FabricClientCommandSource?>) : Events() {
    fun register(command: LiteralArgumentBuilder<FabricClientCommandSource?>) {
        dispatcher.register(command)
    }
}