package gobby.commands.developer

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PacketUtils.getSequence
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.timer.Executor
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand

object TestPearl {

    private fun throwPearl(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal(name)
            .executes {

                val yaw = 38f
                val pitch = 1.5f

                modMessage("Sending interact packet with sequence: ${getSequence()}")
                val sendInteract = ServerboundUseItemPacket(InteractionHand.MAIN_HAND, getSequence(), yaw, pitch)
                mc.connection?.send(sendInteract)

                // TODO: Look into TeleportConfirmC2SPacket
                Command.SINGLE_SUCCESS
            }
    }

    private fun rightClick(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommands.literal(name)
            .executes {
                val task = Executor.schedule(60) {
                    modMessage("Right Clicking ")
                    rightClick()
                }
                Command.SINGLE_SUCCESS
            }
    }

    @SubscribeEvent
    fun register(event: CommandRegisterEvent) {
        event.register(throwPearl("throwpearl"))
        event.register(rightClick("rcplease"))
    }
}
