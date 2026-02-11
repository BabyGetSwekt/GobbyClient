package gobby.features.skyblock

import gobby.events.ClientTickEvent
import gobby.events.PacketSentEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PacketUtils.getSequence
import gobby.utils.hasItemID
import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object BowCharger {

//    private var charging = false
//    private var chargeTicks = 0
//    private var cooldownTicks = 0
//
//    @SubscribeEvent
//    fun onTick(event: ClientTickEvent) {
//        val client = MinecraftClient.getInstance() ?: return
//
//        if (client.player == null || client.networkHandler == null) return
//        if (!client.player!!.mainHandStack.hasItemID("minecraft:bow")) return
//
//        if (cooldownTicks > 0) {
//            cooldownTicks--
//            return
//        }
//
//        if (!charging) {
//            // Start charging the bow
//            val packet = PlayerInteractItemC2SPacket(
//                Hand.MAIN_HAND,
//                getSequence(),
//                180f,
//                -90f
//            )
//            client.networkHandler!!.sendPacket(packet)
//            modMessage("Started charging bow.")
//            charging = true
//            chargeTicks = 0
//        } else {
//            chargeTicks++
//
//            if (chargeTicks >= 30) {
//                // Release the bow
//                val packet = PlayerActionC2SPacket(
//                    PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
//                    BlockPos.ORIGIN,
//                    Direction.DOWN,
//                    0
//                )
//                client.networkHandler!!.sendPacket(packet)
//                modMessage("Released bow after $chargeTicks ticks.")
//
//                // Start cooldown to ensure next charge works
//                charging = false
//                cooldownTicks = 1
//            }
//        }
//    }
}
