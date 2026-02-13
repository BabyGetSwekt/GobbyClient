package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.utils.Utils.equalsOneOf
import gobby.utils.VecUtils.addVec
import gobby.utils.VecUtils.rotateAroundNorth
import gobby.utils.VecUtils.rotateToNorth
import gobby.utils.VecUtils.subtractVec
import gobby.utils.VecUtils.toBlockPos
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

    fun isSecret(pos: BlockPos): Boolean {
        val world = mc.world ?: return false
        val block = world.getBlockState(pos).block
        if (block.equalsOneOf(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.LEVER)) return true

        if (block is AbstractSkullBlock) {
            val blockEntity = world.getBlockEntity(pos) as? SkullBlockEntity ?: return false
            val owner = blockEntity.owner ?: return false
            return owner.gameProfile.id?.toString()?.equalsOneOf(WITHER_ESSENCE_ID, REDSTONE_KEY) == true
        }
        return false
    }

    fun Room.getRelativeCoords(pos: Vec3i) = pos.subtractVec(x = clayPos.x, z = clayPos.z).rotateToNorth(rotation)
    fun Room.getRealCoords(pos: Vec3i) = pos.rotateAroundNorth(rotation).addVec(x = clayPos.x, z = clayPos.z)
    fun Room.getRelativeCoords(pos: BlockPos) = getRelativeCoords(Vec3i(pos.x, pos.y, pos.z)).toBlockPos()
    fun Room.getRealCoords(pos: BlockPos) = getRealCoords(Vec3i(pos.x, pos.y, pos.z)).toBlockPos()
    fun Room.getRelativeCoords(x: Int, y: Int, z: Int) = getRelativeCoords(Vec3i(x, y, z)).toBlockPos()
    fun Room.getRealCoords(x: Int, y: Int, z: Int) = getRealCoords(Vec3i(x, y, z)).toBlockPos()

}