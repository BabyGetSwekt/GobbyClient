package gobby.commands

import gobby.Gobbyclient.Companion.config
import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gg.essential.universal.UScreen
import gobby.utils.ChatUtils.modMessage
import gobby.utils.ChatUtils.sendMessage
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object GobbyCommand {

    private fun openConfig(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal(name)
            .executes {
                mc.send { UScreen.displayScreen(config.gui()) }
                Command.SINGLE_SUCCESS
            }
    }

    private fun sendCoordsCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("sendcoords")
                    .executes { context ->
                        val player = mc.player ?: return@executes 0
                        val x = player.x.toInt()
                        val y = player.y.toInt()
                        val z = player.z.toInt()
                        sendMessage("x: $x, y: $y, z: $z")
                        Command.SINGLE_SUCCESS
                    }
            )
    }

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(openConfig("gobby"))
        event.register(openConfig("gobbyclient"))
        event.register(sendCoordsCommand())
    }
}