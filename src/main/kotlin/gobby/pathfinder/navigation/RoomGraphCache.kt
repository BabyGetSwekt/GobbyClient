package gobby.pathfinder.navigation

import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.map.RoomGraph
import java.util.LinkedHashMap

internal object RoomGraphCache {
    private val graphs = object : LinkedHashMap<GraphKey, RoomGraph>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<GraphKey, RoomGraph>?): Boolean = size > MAX_GRAPHS
    }

    @Synchronized
    fun get(grid: Array<MapTile>, revision: Long): RoomGraph {
        val key = key(grid, revision)
        graphs[key]?.let { return it }
        return RoomGraph.build(grid).also { graphs[key] = it }
    }

    @Synchronized
    fun preload(grid: Array<MapTile>, revision: Long) {
        val key = key(grid, revision)
        if (graphs.containsKey(key)) return
        graphs[key] = RoomGraph.build(grid)
    }

    @Synchronized
    fun clear() {
        graphs.clear()
    }

    private fun key(grid: Array<MapTile>, revision: Long) = GraphKey(signature(grid), revision)

    private fun signature(grid: Array<MapTile>): Int = grid.contentHashCode()

    private data class GraphKey(val signature: Int, val revision: Long)

    private const val MAX_GRAPHS = 16
    private const val INITIAL_CAPACITY = 16
    private const val LOAD_FACTOR = 0.75f
}
