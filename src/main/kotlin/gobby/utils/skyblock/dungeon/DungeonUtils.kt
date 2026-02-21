package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.Utils.equalsOneOf
import gobby.utils.Utils.getBlockAtPos
import gobby.utils.Utils.posY
import gobby.utils.VecUtils.addVec
import gobby.utils.VecUtils.rotateAroundNorth
import gobby.utils.VecUtils.rotateToNorth
import gobby.utils.VecUtils.subtractVec
import gobby.utils.VecUtils.toBlockPos
import gobby.utils.isHoldingSkyblockItem
import gobby.utils.swapToSkyblockItem
import gobby.utils.skyblock.dungeon.tiles.Room
import net.minecraft.block.AbstractSkullBlock
import net.minecraft.block.Blocks
import net.minecraft.block.entity.SkullBlockEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

object DungeonUtils {

    const val WITHER_ESSENCE_ID = "e0f3e929-869e-3dca-9504-54c666ee6f23"
    const val REDSTONE_KEY = "fed95410-aba1-39df-9b95-1d4f361eb66e"

    data class DungeonTeammate(
        val name: String,
        val dungeonClass: DungeonClass,
        val classLevel: String,
        val playerLevel: Int,
        val emblem: String? = null
    )

    enum class DungeonClass {
        Healer, Mage, Berserk, Archer, Tank, Unknown
    }

    inline val dungeonTeammates get() = DungeonListener.teammates
    inline val doorOpener get() = DungeonListener.doorOpener

    private const val SPIRIT_LEAP = "SPIRIT_LEAP"
    private const val INFINITE_SPIRIT_LEAP = "INFINITE_SPIRIT_LEAP"

    var leapTarget: String? = null

    fun leapTo(name: String, autoSwap: Boolean = false) {
        val player = mc.player ?: return
        if (name == player.name?.string) {
            errorMessage("GG code tries to leap to itself. Report this to me, thanks")
            return
        }

        if (autoSwap) {
            if (!isHoldingSkyblockItem(SPIRIT_LEAP, INFINITE_SPIRIT_LEAP)) {
                if (!swapToSkyblockItem(SPIRIT_LEAP, INFINITE_SPIRIT_LEAP)) {
                    modMessage("No Spirit Leap found in hotbar!")
                    return
                }
            }
        }

        leapTarget = name
        rightClick()
    }

    fun isSecret(pos: BlockPos): Boolean {
        val world = mc.world ?: return false
        val block = world.getBlockAtPos(pos)
        if (block.equalsOneOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER)) return true

        if (block is AbstractSkullBlock) {
            val blockEntity = world.getBlockEntity(pos) as? SkullBlockEntity ?: return false
            val owner = blockEntity.owner ?: return false
            return owner.gameProfile.id?.toString()?.equalsOneOf(WITHER_ESSENCE_ID, REDSTONE_KEY) == true
        }
        return false
    }

    fun getPhase(): Int {
        if (dungeonFloor != 7 || !inBoss) return 0

        return when {
            posY > 210 -> 1
            posY > 155 -> 2
            posY > 100 -> 3
            posY > 45 -> 4
            else -> 5
        }
    }

    fun Room.getRelativeCoords(pos: Vec3i) = pos.subtractVec(x = clayPos.x, z = clayPos.z).rotateToNorth(rotation)
    fun Room.getRealCoords(pos: Vec3i) = pos.rotateAroundNorth(rotation).addVec(x = clayPos.x, z = clayPos.z)
    fun Room.getRelativeCoords(pos: BlockPos) = getRelativeCoords(Vec3i(pos.x, pos.y, pos.z)).toBlockPos()
    fun Room.getRealCoords(pos: BlockPos) = getRealCoords(Vec3i(pos.x, pos.y, pos.z)).toBlockPos()
    fun Room.getRelativeCoords(x: Int, y: Int, z: Int) = getRelativeCoords(Vec3i(x, y, z)).toBlockPos()
    fun Room.getRealCoords(x: Int, y: Int, z: Int) = getRealCoords(Vec3i(x, y, z)).toBlockPos()

}