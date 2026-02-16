package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.MinecraftClientAccessor
import gobby.mixinterface.IMinecraftClient
import gobby.utils.Utils.posX
import gobby.utils.Utils.posY
import gobby.utils.Utils.posZ
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
        (mc as IMinecraftClient).`gobbyclient$rightClick`()
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