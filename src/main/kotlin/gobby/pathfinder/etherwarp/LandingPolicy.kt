package gobby.pathfinder.etherwarp

import net.minecraft.core.BlockPos

class LandingPolicy(private val maxY: Int, private val allowed: (BlockPos) -> Boolean) {

    fun accepts(hit: BlockPos): Boolean = hit.y <= maxY && allowed(hit)

    companion object {
        val ACCEPT_ALL: (BlockPos) -> Boolean = { true }

        fun upTo(maxY: Int, allowed: (BlockPos) -> Boolean = ACCEPT_ALL) = LandingPolicy(maxY, allowed)

        val UNBOUNDED = LandingPolicy(Int.MAX_VALUE, ACCEPT_ALL)
    }
}
