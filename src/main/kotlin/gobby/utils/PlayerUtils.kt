package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.mixin.accessor.KeyMappingAccessor
import gobby.mixin.accessor.MinecraftAccessor
import gobby.mixinterface.IInteractionManagerAccessor
import gobby.utils.Utils.posX
import gobby.utils.Utils.posY
import gobby.utils.Utils.posZ
import gobby.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.client.KeyMapping
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3


object PlayerUtils {

    const val STANDING_EYE_HEIGHT = 1.62
    const val SNEAK_EYE_HEIGHT = 1.27
    const val SWIMMING_EYE_HEIGHT = 0.4

    fun getEyeHeight(): Double {
        val player = mc.player ?: return STANDING_EYE_HEIGHT
        return when {
            player.pose == Pose.SWIMMING -> SWIMMING_EYE_HEIGHT
            player.isCrouching -> SNEAK_EYE_HEIGHT
            else -> STANDING_EYE_HEIGHT
        }
    }

    fun getEyeHeight(sneaking: Boolean): Double =
        if (sneaking) SNEAK_EYE_HEIGHT else STANDING_EYE_HEIGHT

    fun getSyncedPos(): Vec3? {
        val player = mc.player ?: return null
        return Vec3(player.xo, player.yo, player.zo)
    }

    fun getEyePosition(): Vec3? {
        val player = mc.player ?: return null
        return Vec3(player.x, player.y + getEyeHeight(), player.z)
    }

    fun leftClick() {
        val accessor = mc as MinecraftAccessor
        if (accessor.attackCooldown == 10000) accessor.setAttackCooldown(0)
        val key = (mc.options.keyAttack as KeyMappingAccessor).boundKey
        KeyMapping.set(key, true)
        KeyMapping.click(key)
        KeyMapping.set(key, false)
    }

    fun rightClick() {
        val key = (mc.options.keyUse as KeyMappingAccessor).boundKey
        KeyMapping.set(key, true)
        KeyMapping.click(key)
        KeyMapping.set(key, false)
    }

    fun useItem(yaw: Float, pitch: Float): Boolean {
        val player = mc.player ?: return false
        val world = mc.level ?: return false
        val manager = mc.gameMode ?: return false
        if (player.isSpectator || DungeonUtils.isDead || player.isRemoved) return false
        val accessor = manager as IInteractionManagerAccessor
        accessor.`gobbyclient$syncSelectedSlot`()
        accessor.`gobbyclient$sendSequencedPacket`(world) { sequence ->
            ServerboundUseItemPacket(InteractionHand.MAIN_HAND, sequence, yaw, pitch)
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

fun Player.isNpc(): Boolean = uuid.version() != 4
