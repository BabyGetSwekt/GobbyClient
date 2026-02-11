package gobby.utils.skyblock.dungeon

import gobby.utils.VecUtils.addVec
import gobby.utils.VecUtils.rotateAroundNorth
import gobby.utils.VecUtils.rotateToNorth
import gobby.utils.VecUtils.subtractVec
import gobby.utils.VecUtils.toBlockPos
import gobby.utils.skyblock.dungeon.tiles.Room
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

object DungeonUtils {

    fun Room.getRelativeCoords(pos: Vec3i) = pos.subtractVec(x = clayPos.x, z = clayPos.z).rotateToNorth(rotation)
    fun Room.getRealCoords(pos: Vec3i) = pos.rotateAroundNorth(rotation).addVec(x = clayPos.x, z = clayPos.z)
    fun Room.getRelativeCoords(pos: BlockPos) = getRelativeCoords(Vec3i(pos.x, pos.y, pos.z)).toBlockPos()
    fun Room.getRealCoords(pos: BlockPos) = getRealCoords(Vec3i(pos.x, pos.y, pos.z)).toBlockPos()
    fun Room.getRelativeCoords(x: Int, y: Int, z: Int) = getRelativeCoords(Vec3i(x, y, z)).toBlockPos()
    fun Room.getRealCoords(x: Int, y: Int, z: Int) = getRealCoords(Vec3i(x, y, z)).toBlockPos()

}