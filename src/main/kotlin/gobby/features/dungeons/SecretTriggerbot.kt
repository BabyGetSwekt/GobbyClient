package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.features.Triggerbot
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.equalsOneOf
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.skyblock.dungeon.ScanUtils.currentRoom
import net.minecraft.util.math.BlockPos

object SecretTriggerbot : Triggerbot("Secret Triggerbot", "Automatically right-clicks dungeon secrets", Category.DUNGEONS) {

    override fun shouldActivate(): Boolean {
        if (!inDungeons || inBoss || mc.currentScreen != null) return false
        if (!enabled) return false
        if (currentRoom?.data?.name.equalsOneOf("Water Board", "Three Weirdos")) return false
        return true
    }

    override fun isValidBlock(pos: BlockPos): Boolean = DungeonUtils.isSecret(pos)
}
