package gobby.utils.skyblock.dungeon.map

class RoomGraph private constructor(
    private val canonicalOf: Map<Int, Int>,
    private val components: Map<Int, Set<Int>>,
    private val adjacency: Map<Int, List<Edge>>
) {
    data class Edge(val to: Int, val doorCell: Int, val locked: Boolean)

    fun canonical(cell: Int): Int? = canonicalOf[cell]

    fun component(canonical: Int): Set<Int> = components[canonical].orEmpty()

    fun edges(canonical: Int): List<Edge> = adjacency[canonical].orEmpty()

    companion object {
        private val DIRECTIONS = listOf(
            intArrayOf(0, -1, 0, -2),
            intArrayOf(0, 1, 0, 2),
            intArrayOf(1, 0, 2, 0),
            intArrayOf(-1, 0, -2, 0)
        )

        fun build(grid: Array<MapTile>): RoomGraph {
            val canonicalOf = HashMap<Int, Int>()
            val components = HashMap<Int, Set<Int>>()
            assignComponents(grid, canonicalOf, components)
            return RoomGraph(canonicalOf, components, buildAdjacency(grid, canonicalOf))
        }

        private fun assignComponents(grid: Array<MapTile>, canonicalOf: HashMap<Int, Int>, components: HashMap<Int, Set<Int>>) {
            grid.indices.forEach { cell ->
                if (grid[cell] !is MapTile.Room || cell in canonicalOf) return@forEach
                val component = DungeonRooms.component(grid, cell)
                val canonical = component.minOrNull() ?: cell
                component.forEach { canonicalOf[it] = canonical }
                components[canonical] = component
            }
        }

        private fun buildAdjacency(grid: Array<MapTile>, canonicalOf: Map<Int, Int>): Map<Int, List<Edge>> {
            val result = HashMap<Int, MutableList<Edge>>()
            val seen = HashSet<Pair<Int, Int>>()
            canonicalOf.forEach { (cell, from) ->
                DIRECTIONS.forEach { dir ->
                    val edge = edgeFrom(grid, canonicalOf, cell, dir) ?: return@forEach
                    if (seen.add(from to edge.doorCell)) result.getOrPut(from) { mutableListOf() }.add(edge)
                }
            }
            return result
        }

        private fun edgeFrom(grid: Array<MapTile>, canonicalOf: Map<Int, Int>, cell: Int, dir: IntArray): Edge? {
            val doorCol = MapGrid.col(cell) + dir[0]
            val doorRow = MapGrid.row(cell) + dir[1]
            val farCol = MapGrid.col(cell) + dir[2]
            val farRow = MapGrid.row(cell) + dir[3]
            if (!MapGrid.inRange(farCol, farRow)) return null
            val door = grid[MapGrid.index(doorCol, doorRow)] as? MapTile.Door ?: return null
            val far = canonicalOf[MapGrid.index(farCol, farRow)] ?: return null
            return Edge(far, MapGrid.index(doorCol, doorRow), door.type == DoorType.WITHER || door.type == DoorType.BLOOD)
        }
    }
}
