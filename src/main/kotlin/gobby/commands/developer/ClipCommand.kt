package gobby.commands.developer

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import kotlin.math.cos
import kotlin.math.sin

object ClipCommand {

    private fun clipCommand(): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal("gobby")
            .then(
                ClientCommandManager.literal("clip")
                    .then(
                        ClientCommandManager.argument("blocks", DoubleArgumentType.doubleArg())
                            .executes { context ->
                                val player = mc.player ?: return@executes Command.SINGLE_SUCCESS
                                if (!mc.isInSingleplayer) {
                                    errorMessage("Clip can only be used in singleplayer.")
                                    return@executes Command.SINGLE_SUCCESS
                                }

                                val blocks = DoubleArgumentType.getDouble(context, "blocks")
                                val yaw = Math.toRadians(player.yaw.toDouble())
                                val pitch = Math.toRadians(player.pitch.toDouble())

                                val dx = -sin(yaw) * cos(pitch) * blocks
                                val dy = -sin(pitch) * blocks
                                val dz = cos(yaw) * cos(pitch) * blocks

                                player.setPosition(player.x + dx, player.y + dy, player.z + dz)
                                modMessage("Clipped $blocks blocks.")
                                Command.SINGLE_SUCCESS
                            }
                    )
            )
    }

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(clipCommand())
    }
}
