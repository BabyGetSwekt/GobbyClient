package gobby.features.developer

import gobby.Gobbyclient
import gobby.events.ClientTickEvent
import gobby.events.PacketReceivedEvent
import gobby.events.PacketSentEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PacketUtils
import gobby.utils.Utils.equalsOneOf
import gobby.utils.timer.Executor
import net.minecraft.network.packet.c2s.play.AcknowledgeChunksC2SPacket
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.BundleS2CPacket
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkSentS2CPacket
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket
import net.minecraft.network.packet.s2c.play.EntityAttributesS2CPacket
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket
import net.minecraft.network.packet.s2c.play.EntityS2CPacket
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerRotationS2CPacket
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object PacketDebug {

//    val blacklist = listOf(
//        ClientTickEndC2SPacket::class,
//        PlayerRotationS2CPacket::class,
//        PlayerMoveC2SPacket.PositionAndOnGround::class,
//        PlayerMoveC2SPacket.LookAndOnGround::class,
//        PlayerMoveC2SPacket.Full::class,
//        EntityS2CPacket.MoveRelative::class,
//        EntityVelocityUpdateS2CPacket::class,
//        EntitySetHeadYawS2CPacket::class,
//        ChunkDataS2CPacket::class,
//        EntityPositionSyncS2CPacket::class,
//        EntityS2CPacket.Rotate::class,
//        PlaySoundS2CPacket::class,
//        EntityS2CPacket.RotateAndMoveRelative::class,
//        WorldTimeUpdateS2CPacket::class,
//        EntityAttributesS2CPacket::class,
//        EntityTrackerUpdateS2CPacket::class,
//        PlayerListS2CPacket::class,
//        BundleS2CPacket::class,
//        AcknowledgeChunksC2SPacket::class,
//        EntitiesDestroyS2CPacket::class,
//        BlockUpdateS2CPacket::class,
//        ChatMessageS2CPacket::class,
//        ChatMessageC2SPacket::class,
//        ChunkSentS2CPacket::class,
//        EntityStatusS2CPacket::class,
//        WorldEventS2CPacket::class,
//    )
//
//    @JvmField
//    var charging = true
//
//    var firing = true
//
//
//    @SubscribeEvent
//    fun onPacket(event: PacketSentEvent) {
//        if (event.packet::class.equalsOneOf(blacklist)) return
//        ChatUtils.modMessage("Packet sent: ${event.packet::class.simpleName})")
//
//        if (event.packet is PlayerInteractItemC2SPacket) {
//            ChatUtils.modMessage("Interact item: ${event.packet.sequence}, packet type ${event.packet.packetType}")
//        }
//
//        if (event.packet is PlayerActionC2SPacket) {
//            ChatUtils.modMessage("action is ${event.packet.action}")
//        }
//    }
//
//    @SubscribeEvent
//    fun onPacketReceived(event: PacketReceivedEvent) {
//        if (event.packet::class.equalsOneOf(blacklist)) return
//        ChatUtils.modMessage("Packet received: ${event.packet::class.simpleName})")
//    }
//
//    @SubscribeEvent
//    fun onTick(event: ClientTickEvent) {
//        if (Gobbyclient.Companion.mc.player == null || Gobbyclient.Companion.mc.networkHandler == null || Gobbyclient.Companion.mc.world == null) return
//        if (!charging && firing) {
//            Gobbyclient.Companion.mc.networkHandler!!.sendPacket(
//                PlayerInteractItemC2SPacket(Hand.MAIN_HAND, PacketUtils.getSequence(), 180f, -90f)
//            )
//            firing = false
//            charging = true
//
//
//            val task = Executor.execute(20) {
//                Gobbyclient.Companion.mc.networkHandler!!.sendPacket(
//                    PlayerActionC2SPacket(
//                        PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
//                        BlockPos.ORIGIN,
//                        Direction.DOWN,
//                        0
//                    )
//                )
//                firing = true
//                charging = false
//            }
//        }
//
//    }

}