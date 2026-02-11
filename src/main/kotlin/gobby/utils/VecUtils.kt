package gobby.utils

import gobby.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

object VecUtils {

    data class Vec2(val x: Int, val z: Int)

    fun Vec3d.addVec(
        x: Double = 0.0,
        y: Double = 0.0,
        z: Double = 0.0
    ): Vec3d {
        return Vec3d(this.x + x, this.y + y, this.z + z)
    }

    fun Vec3i.addVec(
        x: Int = 0,
        y: Int = 0,
        z: Int = 0
    ): Vec3i {
        return Vec3i(this.x + x, this.y + y, this.z + z)
    }

    fun Vec3d.subtractVec(x: Number = .0, y: Number = .0, z: Number = .0): Vec3d =
        this.addVec(-x.toDouble(), -y.toDouble(), -z.toDouble())

    fun Vec3i.subtractVec(x: Number = 0, y: Number = 0, z: Number = 0): Vec3i =
        this.addVec(-x.toInt(), -y.toInt(), -z.toInt())

    fun Vec3i.rotateToNorth(rotation: Rotations): Vec3i =
        when (rotation) {
            Rotations.NORTH -> Vec3i(-this.x, this.y, -this.z)
            Rotations.WEST ->  Vec3i(this.z, this.y, -this.x)
            Rotations.SOUTH -> Vec3i(this.x, this.y, this.z)
            Rotations.EAST ->  Vec3i(-this.z, this.y, this.x)
            else -> this
        }

    fun Vec3i.rotateAroundNorth(rotation: Rotations): Vec3i =
        when (rotation) {
            Rotations.NORTH -> Vec3i(-this.x, this.y, -this.z)
            Rotations.WEST ->  Vec3i(-this.z, this.y, this.x)
            Rotations.SOUTH -> Vec3i(this.x, this.y, this.z)
            Rotations.EAST ->  Vec3i(this.z, this.y, -this.x)
            else -> this
        }

    fun Vec3i.toBlockPos(add: Double = 0.0): BlockPos =
        BlockPos((this.x + add).toInt(), (this.y + add).toInt(), (this.z + add).toInt())
}
