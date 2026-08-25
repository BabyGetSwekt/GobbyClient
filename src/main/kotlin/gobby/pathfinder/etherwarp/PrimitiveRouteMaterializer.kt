package gobby.pathfinder.etherwarp

import gobby.pathfinder.search.PrimitiveSearchArena
import net.minecraft.core.BlockPos

internal object PrimitiveRouteMaterializer {
    fun materialize(arena: PrimitiveSearchArena, end: Int): List<EtherwarpNode> {
        val indices = ArrayList<Int>()
        var index = end
        while (index >= 0) {
            indices.add(index)
            index = arena.parent[index]
        }
        indices.reverse()
        return indices.mapIndexed { routeIndex, arenaIndex ->
            val outgoing = indices.getOrNull(routeIndex + 1)
            EtherwarpNode(
                arena.x[arenaIndex],
                arena.y[arenaIndex],
                arena.z[arenaIndex],
                BlockPos.of(arena.positions[arenaIndex]),
                arena.g[arenaIndex],
                arena.f[arenaIndex] - arena.g[arenaIndex],
                null,
                outgoing?.let { arena.yaw[it] } ?: ZERO_AIM,
                outgoing?.let { arena.pitch[it] } ?: ZERO_AIM
            )
        }
    }

    private const val ZERO_AIM = 0f
}
