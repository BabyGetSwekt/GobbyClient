package gobby.pathfinder.etherwarp

import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_STRIDE
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.RoomGraph
import gobby.utils.skyblock.dungeon.tiles.RoomData
import java.util.PriorityQueue
import kotlin.math.abs

data class RoomStep(val data: RoomData, val cellIndex: Int, val doorCell: Int?)

object DungeonRoomPathfinder {

    fun findPath(grid: Array<MapTile>, startCell: Int, goalCell: Int, ignoreLocked: Boolean = false, opened: (Int) -> Boolean = { false }): List<RoomStep>? =
        findPath(grid, RoomGraph.build(grid), startCell, goalCell, ignoreLocked, opened)

    fun findPath(grid: Array<MapTile>, graph: RoomGraph, startCell: Int, goalCell: Int, ignoreLocked: Boolean = false, opened: (Int) -> Boolean = { false }): List<RoomStep>? {
        val start = graph.canonical(startCell) ?: return null
        val goal = graph.canonical(goalCell) ?: return null
        return RoomSearch(grid, graph, goal, ignoreLocked, opened).run(start)
    }

    fun reachableFrom(grid: Array<MapTile>, startCell: Int, ignoreLocked: Boolean = false, opened: (Int) -> Boolean = { false }): Set<Int> {
        val graph = RoomGraph.build(grid)
        val start = graph.canonical(startCell) ?: return emptySet()
        val seen = hashSetOf(start)
        val queue = ArrayDeque<Int>().apply { add(start) }
        while (queue.isNotEmpty()) {
            graph.edges(queue.removeFirst()).forEach { edge ->
                if (passable(edge, ignoreLocked, opened) && seen.add(edge.to)) queue.add(edge.to)
            }
        }
        return seen
    }

    fun reachableCellsFrom(grid: Array<MapTile>, startCell: Int, ignoreLocked: Boolean = false, opened: (Int) -> Boolean = { false }): Set<Int> =
        reachableFrom(grid, startCell, ignoreLocked, opened).flatMapTo(hashSetOf()) { DungeonRooms.component(grid, it) }

    private class RoomSearch(
        private val grid: Array<MapTile>,
        private val graph: RoomGraph,
        private val goal: Int,
        private val ignoreLocked: Boolean,
        private val opened: (Int) -> Boolean
    ) {
        private val open = PriorityQueue<RoomNode>()
        private val bestG = HashMap<Int, Int>()

        fun run(start: Int): List<RoomStep>? {
            if (start == goal) return listOf(step(start, null))
            push(RoomNode(start, -1, 0, heuristic(start), null))
            while (open.isNotEmpty()) {
                val current = open.poll()
                if (bestG[current.cell]?.let { current.g > it } == true) continue
                if (current.cell == goal) return reconstruct(current)
                relax(current)
            }
            return null
        }

        private fun relax(current: RoomNode) {
            graph.edges(current.cell).forEach { edge ->
                if (!passable(edge, ignoreLocked, opened)) return@forEach
                val g = current.g + 1
                if (bestG[edge.to]?.let { g >= it } != true) push(RoomNode(edge.to, edge.doorCell, g, heuristic(edge.to), current))
            }
        }

        private fun push(node: RoomNode) {
            bestG[node.cell] = node.g
            open.add(node)
        }

        private fun heuristic(cell: Int): Int =
            (abs(MapGrid.col(cell) - MapGrid.col(goal)) + abs(MapGrid.row(cell) - MapGrid.row(goal))) / CELL_STRIDE

        private fun step(cell: Int, door: Int?): RoomStep = RoomStep((grid[cell] as MapTile.Room).data, cell, door)

        private tailrec fun reconstruct(node: RoomNode?, nextDoor: Int? = null, acc: MutableList<RoomStep> = mutableListOf()): List<RoomStep> {
            if (node == null) return acc
            acc.add(0, step(node.cell, nextDoor))
            return reconstruct(node.parent, node.doorFromParent.takeIf { it >= 0 }, acc)
        }
    }
}

private fun passable(edge: RoomGraph.Edge, ignoreLocked: Boolean, opened: (Int) -> Boolean): Boolean =
    !edge.locked || ignoreLocked || opened(edge.doorCell)

private class RoomNode(
    val cell: Int,
    val doorFromParent: Int,
    val g: Int,
    val h: Int,
    val parent: RoomNode?
) : Comparable<RoomNode> {
    val f: Int get() = g + h
    override fun compareTo(other: RoomNode): Int = f.compareTo(other.f)
}
