package gobby.commands

import com.mojang.brigadier.Command
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import gobby.features.dungeons.Brush
import gobby.utils.ChatUtils.modMessage
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager

object BrushCommand {

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(
            ClientCommandManager.literal("gobby")
                .then(
                    ClientCommandManager.literal("brush")
                        .executes {
                            Brush.enabled = !Brush.enabled
                            modMessage(if (Brush.enabled) "Brush mode §aenabled" else "Brush mode §cdisabled")
                            Command.SINGLE_SUCCESS
                        }
                )
        )
    }
}
