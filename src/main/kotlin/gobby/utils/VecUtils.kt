package gobby.utils

import gobby.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.core.Vec3i
import kotlin.math.sqrt

object VecUtils {

    data class Vec2(val x: Int, val z: Int)

    fun lengthSq(dx: Double, dy: Double, dz: Double): Double = dx * dx + dy * dy + dz * dz

    fun distanceSq(a: BlockPos, b: BlockPos): Double = lengthSq((a.x - b.x).toDouble(), (a.y - b.y).toDouble(), (a.z - b.z).toDouble())

    fun distance(a: BlockPos, b: BlockPos): Double = sqrt(distanceSq(a, b))

    fun centerDistanceSq(from: Vec3, to: BlockPos): Double = lengthSq(to.x + 0.5 - from.x, to.y + 0.5 - from.y, to.z + 0.5 - from.z)

    fun Vec3.addVec(
        x: Double = 0.0,
        y: Double = 0.0,
        z: Double = 0.0
    ): Vec3 {
        return Vec3(this.x + x, this.y + y, this.z + z)
    }

    fun Vec3i.addVec(
        x: Int = 0,
        y: Int = 0,
        z: Int = 0
    ): Vec3i {
        return Vec3i(this.x + x, this.y + y, this.z + z)
    }

    fun Vec3.subtractVec(x: Number = .0, y: Number = .0, z: Number = .0): Vec3 =
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

    fun Vec3.rotateAroundNorth(rotation: Rotations): Vec3 =
        when (rotation) {
            Rotations.NORTH -> Vec3(-this.x, this.y, -this.z)
            Rotations.WEST ->  Vec3(-this.z, this.y, this.x)
            Rotations.SOUTH -> Vec3(this.x, this.y, this.z)
            Rotations.EAST ->  Vec3(this.z, this.y, -this.x)
            else -> this
        }

    fun Vec3.rotateToNorth(rotation: Rotations): Vec3 =
        when (rotation) {
            Rotations.NORTH -> Vec3(-this.x, this.y, -this.z)
            Rotations.WEST ->  Vec3(this.z, this.y, -this.x)
            Rotations.SOUTH -> Vec3(this.x, this.y, this.z)
            Rotations.EAST ->  Vec3(-this.z, this.y, this.x)
            else -> this
        }

    fun Vec3i.toBlockPos(add: Double = 0.0): BlockPos =
        BlockPos((this.x + add).toInt(), (this.y + add).toInt(), (this.z + add).toInt())
}
