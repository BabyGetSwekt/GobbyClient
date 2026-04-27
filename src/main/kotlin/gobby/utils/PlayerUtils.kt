package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.KeyBindingAccessor
import gobby.mixin.accessor.MinecraftClientAccessor
import gobby.mixinterface.IInteractionManagerAccessor
import gobby.utils.Utils.posX
import gobby.utils.Utils.posY
import gobby.utils.Utils.posZ
import gobby.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.client.option.KeyBinding
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d


object PlayerUtils {

    fun getEyePosition(): Vec3d? {
        val player = mc.player ?: return null
        return Vec3d(player.x, player.eyeY, player.z)
    }

    fun leftClick() {
        val attackCooldown: Int = (mc as MinecraftClientAccessor).getAttackCooldown()
        if (attackCooldown == 10000) {
            (mc as MinecraftClientAccessor).setAttackCooldown(0)
        }
        mc.options.attackKey.isPressed = true
        (mc as MinecraftClientAccessor).leftClick()
        mc.options.attackKey.isPressed = false
    }

    fun rightClick() {
        val key = (mc.options.useKey as KeyBindingAccessor).boundKey
        KeyBinding.setKeyPressed(key, true)
        KeyBinding.onKeyPressed(key)
        KeyBinding.setKeyPressed(key, false)
    }

    fun useItem(yaw: Float, pitch: Float): Boolean {
        val player = mc.player ?: return false
        val world = mc.world ?: return false
        val manager = mc.interactionManager ?: return false
        if (player.isSpectator || DungeonUtils.isDead || player.isDead) return false
        val accessor = manager as IInteractionManagerAccessor
        accessor.`gobbyclient$syncSelectedSlot`()
        accessor.`gobbyclient$sendSequencedPacket`(world) { sequence ->
            PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch)
        }
        return true
    }


    fun isPlayerInBox(x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double): Boolean {
        val playerX = posX
        val playerY = mc.player?.eyeY ?: posY
        val playerZ = posZ
        return playerX in x1..x2 && playerY in y1..y2 && playerZ in z1..z2
    }

    fun isPlayerInBox(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): Boolean {
        val playerX = posX.toInt()
        val playerY = (mc.player?.eyeY ?: posY).toInt()
        val playerZ = posZ.toInt()
        return playerX in x1..x2 && playerY in y1..y2 && playerZ in z1..z2
    }
}