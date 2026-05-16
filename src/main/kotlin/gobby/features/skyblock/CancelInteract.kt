package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.RightClickEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PacketUtils.getSequence
import gobby.utils.Utils.equalsOneOf
import gobby.utils.hasItemID
import net.minecraft.world.level.block.Blocks
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

object CancelInteract : Module("Cancel Interact", "Cancels block interaction so you can throw pearls freely", Category.SKYBLOCK) {

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        if (mc.level == null || mc.player == null || !enabled) return
        val hitResult = mc.hitResult
        if (hitResult !is BlockHitResult || hitResult.type != HitResult.Type.BLOCK) return
        val player = mc.player ?: return
        val yaw = player.yRot
        val pitch = player.xRot

        val pos = hitResult.blockPos ?: return
        val block = mc.level?.getBlockState(pos)?.block ?: return
        if (!player.mainHandItem.hasItemID("minecraft:ender_pearl")) return
        if (block.equalsOneOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER, Blocks.OAK_BUTTON, Blocks.STONE_BUTTON)) return
        val sendInteract = ServerboundUseItemPacket(InteractionHand.MAIN_HAND, getSequence(), yaw, pitch)
        mc.connection?.send(sendInteract)

    }

}
