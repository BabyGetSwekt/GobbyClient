package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.RightClickEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PacketUtils.getSequence
import gobby.utils.Utils.equalsOneOf
import gobby.utils.hasItemID
import net.minecraft.block.Blocks
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult

object CancelInteract {

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        if (mc.world == null || mc.player == null || !GobbyConfig.cancelInteract) return
        val hitResult = mc.crosshairTarget
        if (hitResult !is BlockHitResult || hitResult.type != HitResult.Type.BLOCK) return
        val player = mc.player ?: return
        val yaw = player.yaw
        val pitch = player.pitch

        val pos = hitResult.blockPos ?: return
        val block = mc.world?.getBlockState(pos)?.block ?: return
        if (!player.mainHandStack.hasItemID("minecraft:ender_pearl")) return
        if (block.equalsOneOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER, Blocks.OAK_BUTTON, Blocks.STONE_BUTTON)) return
        val sendInteract = PlayerInteractItemC2SPacket(Hand.MAIN_HAND, getSequence(), yaw, pitch)
        mc.networkHandler?.sendPacket(sendInteract)

    }

}