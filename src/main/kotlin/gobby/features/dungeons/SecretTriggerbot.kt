 package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.features.Triggerbot
import gobby.gui.click.Category
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.equalsOneOf
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.ScanUtils.currentRoom
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.entity.ChestLidController
import net.minecraft.core.BlockPos

object SecretTriggerbot : Triggerbot("Secret Triggerbot", "Automatically right-clicks dungeon secrets", Category.DUNGEONS) {

    private val lidAnimatorField by lazy {
        ChestBlockEntity::class.java.declaredFields.first { it.type == ChestLidController::class.java }.apply { isAccessible = true }
    }

    override fun shouldActivate(): Boolean {
        if (!inDungeons || inBoss || mc.gui.screen() != null) return false
        if (!enabled) return false
        if (currentRoom?.data?.name.equalsOneOf("Water Board", "Three Weirdos")) return false
        return true
    }

    override fun isValidBlock(pos: BlockPos): Boolean {
        if (!DungeonUtils.isSecret(pos)) return false
        val world = mc.level ?: return false
        if (world.getBlockState(pos).block is ChestBlock) {
            val be = world.getBlockEntity(pos) as? ChestBlockEntity ?: return false
            val animator = lidAnimatorField.get(be) as? ChestLidController ?: return false
            if (animator.getOpenness(0f) > 0f) return false
        }
        return true
    }
}
