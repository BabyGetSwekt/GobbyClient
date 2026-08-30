package gobby.commands.developer

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.Gobbyclient
import gobby.Gobbyclient.Companion.mc
import gobby.events.ChatReceivedEvent
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import gobby.events.network.SystemChatReceivedEvent
import gobby.utils.ChatUtils.noControlCodes
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.chat.Component

object SimulateCommand {

    private fun simulateCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal("gobby")
            .then(
                ClientCommands.literal("simulate")
                    .then(
                        ClientCommands.argument("message", StringArgumentType.greedyString())
                            .executes { context ->
                                simulate(StringArgumentType.getString(context, "message"))
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
    }

    private fun simulate(message: String) {
        val content = Component.literal(message)
        val plain = message.noControlCodes
        val hidden = Gobbyclient.EVENT_MANAGER.publish(ChatReceivedEvent(plain)).isCanceled
        Gobbyclient.EVENT_MANAGER.publish(SystemChatReceivedEvent(plain, content, false))
        if (!hidden) mc.gui.hud.chat.addClientSystemMessage(content)
    }

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(simulateCommand())
    }
}
