package gobby.features.dungeons

import gobby.features.floor7.P3Levers
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.block.entity.SkullBlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView

object SecretHitbox : Module(
    "Secret Hitbox", "Extends secret block hitboxes to a full block",
    Category.DUNGEONS
) {

    val lever by BooleanSetting("Lever", true, desc = "Extend lever hitbox to a full block")
    val skulls by BooleanSetting("Essence & Skulls", true, desc = "Extend wither essence and redstone skull hitbox to a full block")
    val f7levers by BooleanSetting("F7 Levers", false, desc = "Makes lever in F7 bossfight (lights device and s1-4 levers) have a full block hitbox aswell")

    @JvmField
    var inCollisionCheck = false

    fun shouldExpand(): Boolean = enabled && inDungeons && !inBoss

    fun shouldExpandLever(pos: BlockPos): Boolean {
        val normalLeverHitbox = shouldExpand() && lever
        val f7LeverHitbox = enabled && f7levers && dungeonFloor == 7 && inBoss &&
            P3Levers.leverPositions.containsKey(pos)
        return normalLeverHitbox || f7LeverHitbox
    }

    fun isSecretSkull(world: BlockView, pos: BlockPos): Boolean {
        if (!shouldExpand() || !skulls) return false
        val blockEntity = world.getBlockEntity(pos) as? SkullBlockEntity ?: return false
        val id = blockEntity.owner?.gameProfile?.id?.toString() ?: return false
        return id == DungeonUtils.WITHER_ESSENCE_ID || id == DungeonUtils.REDSTONE_KEY
    }
}
