package gobby.pathfinder.etherwarp

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.hypot

internal object EtherwarpHopLowerBound {
    private const val BLOCK_CENTER = 0.5
    private const val MIN_HOPS = 1

    fun forRoute(from: Vec3, goal: BlockPos, range: Double): Int {
        if (range <= 0.0) return Int.MAX_VALUE
        val horizontalDistance = hypot(goal.x + BLOCK_CENTER - from.x, goal.z + BLOCK_CENTER - from.z)
        return maxOf(MIN_HOPS, ceil(horizontalDistance / range).toInt())
    }
}
