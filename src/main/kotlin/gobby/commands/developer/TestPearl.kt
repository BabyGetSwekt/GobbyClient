package gobby.commands.developer

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gobby.Gobbyclient.Companion.mc
import gobby.events.CommandRegisterEvent
import gobby.events.core.SubscribeEvent
import gobby.mixinterface.IClientConnectionAccessor
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PacketUtils.getSequence
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.timer.Executor
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand

object TestPearl {

    private fun throwPearl(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal(name)
            .executes {

                val yaw = 38f
                val pitch = 1.5f

                modMessage("Sending interact packet with sequence: ${getSequence()}")
                val sendInteract = PlayerInteractItemC2SPacket(Hand.MAIN_HAND, getSequence(), yaw, pitch)
                mc.networkHandler?.sendPacket(sendInteract)

                // TODO: Look into TeleportConfirmC2SPacket
                Command.SINGLE_SUCCESS
            }
    }

    private fun rightClick(name: String): LiteralArgumentBuilder<FabricClientCommandSource?> {
        return ClientCommandManager.literal(name)
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
