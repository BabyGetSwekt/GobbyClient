package gobby.pathfinder.etherwarp

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.core.BlockPos
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger

class EtherwarpContext(
    val goal: BlockPos,
    val range: Double,
    val hWeight: Double,
    val raycasts: Raycasts,
    private val deadline: SearchDeadline,
    val reached: (BlockPos) -> Boolean = { it == goal },
    val maxLandingY: Int = Int.MAX_VALUE
) {
    private val openSet = PriorityQueue<EtherwarpNode>()
    private val nodeMap = Long2ObjectOpenHashMap<EtherwarpNode>()
    private var activeCount = 0

    @Volatile var solved = false
    @Volatile var timedOut = false
    @Volatile var result: List<EtherwarpNode>? = null
    val processed = AtomicInteger(0)

    val elapsed: Long get() = deadline.elapsed
    val expired: Boolean get() = deadline.expired

    val done: Boolean
        @Synchronized get() = openSet.isEmpty() && activeCount == 0

    @Synchronized
    fun next(): EtherwarpNode? {
        while (openSet.isNotEmpty()) {
            val node = openSet.poll()
            val best = nodeMap.get(node.pos.asLong())
            if (best != null && node.g > best.g) continue
            activeCount++
            return node
        }
        return null
    }

    @Synchronized
    fun finish() {
        activeCount--
    }

    @Synchronized
    fun offer(node: EtherwarpNode) {
        if (solved) return
        val key = node.pos.asLong()
        val existing = nodeMap.get(key)
        if (existing == null || node.g < existing.g) {
            nodeMap.put(key, node)
            openSet.add(node)
        }
    }

    @Synchronized
    fun publish(path: List<EtherwarpNode>): Boolean {
        if (solved) return false
        result = path
        solved = true
        return true
    }
}
