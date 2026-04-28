 package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.features.Triggerbot
import gobby.gui.click.Category
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.equalsOneOf
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.ScanUtils.currentRoom
import net.minecraft.block.ChestBlock
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.block.entity.ChestLidAnimator
import net.minecraft.util.math.BlockPos

object SecretTriggerbot : Triggerbot("Secret Triggerbot", "Automatically right-clicks dungeon secrets", Category.DUNGEONS) {

    private val lidAnimatorField by lazy {
        ChestBlockEntity::class.java.declaredFields.first { it.type == ChestLidAnimator::class.java }.apply { isAccessible = true }
    }

    override fun shouldActivate(): Boolean {
        if (!inDungeons || inBoss || mc.currentScreen != null) return false
        if (!enabled) return false
        if (currentRoom?.data?.name.equalsOneOf("Water Board", "Three Weirdos")) return false
        return true
    }

    override fun isValidBlock(pos: BlockPos): Boolean {
        if (!DungeonUtils.isSecret(pos)) return false
        val world = mc.world ?: return false
        if (world.getBlockState(pos).block is ChestBlock) {
            val be = world.getBlockEntity(pos) as? ChestBlockEntity ?: return false
            val animator = lidAnimatorField.get(be) as? ChestLidAnimator ?: return false
            if (animator.getProgress(0f) > 0f) return false
        }
        return true
    }
}
