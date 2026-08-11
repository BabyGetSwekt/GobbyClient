package gobby.pathfinder.etherwarp

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

class EtherwarpNode(
    val x: Double,
    val y: Double,
    val z: Double,
    val pos: BlockPos,
    var g: Double,
    var h: Double,
    var parent: EtherwarpNode?,
    var yaw: Float,
    var pitch: Float
) : Comparable<EtherwarpNode> {
    val eye: Vec3 by lazy(LazyThreadSafetyMode.NONE) { Vec3(x, y, z) }
    val f: Double get() = g + h
    override fun compareTo(other: EtherwarpNode): Int = f.compareTo(other.f)
}

data class Aim(val yaw: Float, val pitch: Float)
