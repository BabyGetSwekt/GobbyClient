package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import net.minecraft.world.phys.Vec3

internal object EtherwarpPathSmoother {
    fun smooth(path: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, cache: BlockCache.SnapshotView?): List<EtherwarpNode> {
        if (path.size < MIN_PATH_SIZE) return path
        val aims = HashMap<Long, Aim>()
        val rangeSq = range * range
        val eyeHeight = kind.eyeHeight()
        val indices = minimumHopRouteIndices(path.size) { fromIndex, toIndex ->
            val source = path[fromIndex]
            val eye = Vec3(source.x, source.y + eyeHeight, source.z)
            val aim = if (VecUtils.centerDistanceSq(eye, path[toIndex].pos) <= rangeSq) {
                kind.aimAt(eye, path[toIndex].pos, range, snapshot = cache)
            } else null
            val resolved = aim ?: if (toIndex == fromIndex + 1) Aim(source.yaw, source.pitch) else null
            resolved?.also { aims[edgeKey(fromIndex, toIndex)] = it } != null
        }
        return indices.mapIndexed { routeIndex, sourceIndex ->
            val source = path[sourceIndex]
            val next = indices.getOrNull(routeIndex + 1)
            val aim = next?.let { aims[edgeKey(sourceIndex, it)] }
            EtherwarpNode(source.x, source.y, source.z, source.pos, source.g, source.h, null, aim?.yaw ?: ZERO_ANGLE, aim?.pitch ?: ZERO_ANGLE)
        }
    }

    internal fun minimumHopRouteIndices(size: Int, canReach: (Int, Int) -> Boolean): List<Int> {
        if (size <= 0) return emptyList()
        if (size == 1) return listOf(0)
        val bestHops = IntArray(size) { Int.MAX_VALUE }
        val next = IntArray(size) { NO_INDEX }
        bestHops[size - 1] = 0
        for (from in size - 2 downTo 0) selectBestNext(from, size, bestHops, next, canReach)
        if (next[0] < 0) return (0 until size).toList()
        val result = ArrayList<Int>(size)
        var index = 0
        result += index
        while (index < size - 1) {
            index = next[index]
            if (index < 0) return (0 until size).toList()
            result += index
        }
        return result.takeIf { it.lastOrNull() == size - 1 } ?: (0 until size).toList()
    }

    private fun selectBestNext(
        from: Int,
        size: Int,
        bestHops: IntArray,
        next: IntArray,
        canReach: (Int, Int) -> Boolean
    ) {
        for (to in from + 1 until size) {
            if (bestHops[to] == Int.MAX_VALUE || !canReach(from, to)) continue
            val candidate = bestHops[to] + 1
            if (candidate < bestHops[from] || candidate == bestHops[from] && to > next[from]) {
                bestHops[from] = candidate
                next[from] = to
            }
        }
    }

    private fun edgeKey(from: Int, to: Int): Long = (from.toLong() shl INT_BITS) or (to.toLong() and INDEX_MASK)

    private const val MIN_PATH_SIZE = 2
    private const val INT_BITS = 32
    private const val INDEX_MASK = 0xffffffffL
    private const val NO_INDEX = -1
    private const val ZERO_ANGLE = 0f
}
