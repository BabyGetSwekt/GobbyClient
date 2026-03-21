package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.PlayerUtils.rightClick
import gobby.utils.Utils.equalsOneOf
import gobby.utils.Utils.getBlockAtPos
import gobby.utils.Utils.posX
import gobby.utils.Utils.posY
import gobby.utils.Utils.posZ
import gobby.utils.VecUtils.addVec
import gobby.utils.VecUtils.rotateAroundNorth
import gobby.utils.VecUtils.rotateToNorth
import gobby.utils.VecUtils.subtractVec
import gobby.utils.VecUtils.toBlockPos
import gobby.utils.isHoldingSkyblockItem
import gobby.utils.skyblockID
import gobby.utils.swapToSkyblockItem
import gobby.utils.skyblock.dungeon.tiles.Room
import net.minecraft.item.ItemStack
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

    enum class DungeonPad {
        Green, Yellow, Purple, Red, None
    }

    enum class Relic(
        val skyblockID: String,
        val spawnPos: Vec3d,
        val cauldronPos: BlockPos
    ) {
        Red("RED_KING_RELIC", Vec3d(22.5, 6.5, 59.5), BlockPos(51, 7, 42)),
        Green("GREEN_KING_RELIC", Vec3d(20.5, 6.5, 94.5), BlockPos(49, 7, 44)),
        Blue("BLUE_KING_RELIC", Vec3d(91.5, 6.5, 94.5), BlockPos(59, 7, 44)),
        Orange("ORANGE_KING_RELIC", Vec3d(90.5, 6.5, 56.5), BlockPos(57, 7, 42)),
        Purple("PURPLE_KING_RELIC", Vec3d(56.5, 8.5, 132.5), BlockPos(54, 7, 41)),
        None("", Vec3d(0.0, 0.0, 0.0), BlockPos(0, 0, 0));

        companion object {
            fun fromItemID(id: String?): Relic =
                entries.firstOrNull { it.skyblockID == id } ?: None
        }
    }

    inline val dungeonTeammates get() = DungeonListener.teammates
    inline val doorOpener get() = DungeonListener.doorOpener
    inline val inP3 get() = DungeonListener.inP3
    inline val isDead: Boolean
        get() = mc.player?.inventory?.getStack(0)?.skyblockID == "HAUNT_ABILITY"

    private val secretDrops = listOf("DUNGEON_DECOY", "TRAINING_WEIGHTS", "SPIRIT_LEAP",
        "DEFUSE_KIT", "CANDYCOMB", "ARCHITECT_FIRST_DRAFT", "INFLATABLE_JERRY", "DUNGEON_TRAP",
        "DYE_SECRET", "DUNGEON_CHEST_KEY", "POTION", "TREASURE_TALISMAN", "REVIVE_STONE")
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

    fun ItemStack.isSecret(): Boolean =
        skyblockID in secretDrops

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

    fun getSection(): Int {
        if (dungeonFloor != 7 || !inBoss || getPhase() != 3) return 0

        return when {
            posX in 90.0..110.0 && posZ in 52.0..121.0 -> 1
            posX in 19.0..88.0 && posZ in 122.0..142.0 -> 2
            posX in -2.0..19.0 && posZ in 52.0..121.0 -> 3
            posX in 19.0..88.0 && posZ in 30.0..50.0 -> 3
            else -> 0
        }
    }

    fun getCurrentPad(): DungeonPad {
        if (dungeonFloor != 7 || !inBoss || getPhase() != 2) return DungeonPad.None

        return when {
            posX in 22.0..56.0 && posZ in 86.0..106.0 -> DungeonPad.Yellow
            posX in 88.0..125.0 && posZ in 86.0..106.0 -> DungeonPad.Purple
            posX in 88.0..125.0 && posZ in 0.0..20.0 -> DungeonPad.Red
            posX in 22.0..56.0 && posZ in 0.0..20.0 -> DungeonPad.Green
            else -> DungeonPad.None
        }
    }

    fun Room.getRelativeCoords(pos: Vec3i) = pos.subtractVec(x = clayPos.x, z = clayPos.z).rotateToNorth(rotation)
    fun Room.getRealCoords(pos: Vec3i) = pos.rotateAroundNorth(rotation).addVec(x = clayPos.x, z = clayPos.z)
    fun Room.getRelativeCoords(pos: BlockPos) = getRelativeCoords(Vec3i(pos.x, pos.y, pos.z)).toBlockPos()
    fun Room.getRealCoords(pos: BlockPos) = getRealCoords(Vec3i(pos.x, pos.y, pos.z)).toBlockPos()
    fun Room.getRelativeCoords(x: Int, y: Int, z: Int) = getRelativeCoords(Vec3i(x, y, z)).toBlockPos()
    fun Room.getRealCoords(x: Int, y: Int, z: Int) = getRealCoords(Vec3i(x, y, z)).toBlockPos()

}