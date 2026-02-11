package gobby.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

object RotateCommand {

    private fun setRotation(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal(name)
            .executes {
                mc.player?.rotate(180f, false, 0f, false)
                Command.SINGLE_SUCCESS
            }
    }

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(setRotation("setrotation"))
    }
}