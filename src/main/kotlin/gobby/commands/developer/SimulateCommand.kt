package gobby.commands.developer

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object SimulateCommand {

    private fun simulateCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("simulate")
                    .then(
                        ClientCommandManager.argument("message", StringArgumentType.greedyString())
                            .executes { context ->
                                val message = StringArgumentType.getString(context, "message")
                                mc.gui.chat.addMessage(Component.literal(message))
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
    }

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(simulateCommand())
    }
}
