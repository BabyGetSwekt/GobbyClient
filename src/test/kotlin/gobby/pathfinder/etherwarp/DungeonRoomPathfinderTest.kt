package gobby.pathfinder.etherwarp

import gobby.utils.skyblock.dungeon.map.DoorType
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.map.RoomGraph
import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.RoomType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DungeonRoomPathfinderTest {

    private fun grid() = Array<MapTile>(GRID_SIZE * GRID_SIZE) { MapTile.Empty }
    private fun cell(col: Int, row: Int) = row * GRID_SIZE + col
    private fun room(name: String) = MapTile.Room(RoomData(name, RoomType.NORMAL, emptyList(), 0, 0, 0), name.hashCode())

    private operator fun Array<MapTile>.set(col: Int, row: Int, tile: MapTile) {
        this[cell(col, row)] = tile
    }

    private fun corridor(): Array<MapTile> = grid().apply {
        set(0, 0, room("A"))
        set(1, 0, MapTile.Door(DoorType.NORMAL))
        set(2, 0, room("B"))
        set(3, 0, MapTile.Door(DoorType.WITHER))
        set(4, 0, room("C"))
    }

    @Test
    fun findsPathThroughNormalDoor() {
        val path = DungeonRoomPathfinder.findPath(corridor(), cell(0, 0), cell(2, 0))
        assertNotNull(path)
        assertEquals(2, path.size)
        assertEquals(cell(1, 0), path[0].doorCell)
        assertNull(path[1].doorCell)
    }

    @Test
    fun witherDoorBlocksByDefault() {
        assertNull(DungeonRoomPathfinder.findPath(corridor(), cell(0, 0), cell(4, 0)))
    }

    @Test
    fun openedWitherDoorIsTraversable() {
        val opened = cell(3, 0)
        val path = DungeonRoomPathfinder.findPath(corridor(), cell(0, 0), cell(4, 0)) { it == opened }
        assertNotNull(path)
        assertEquals(3, path.size)
        assertEquals(cell(3, 0), path[1].doorCell)
    }

    @Test
    fun ignoreLockedTraversesWitherDoor() {
        assertNotNull(DungeonRoomPathfinder.findPath(corridor(), cell(0, 0), cell(4, 0), ignoreLocked = true))
    }

    @Test
    fun reachableStopsAtLockedDoor() {
        val reachable = DungeonRoomPathfinder.reachableFrom(corridor(), cell(0, 0))
        assertEquals(setOf(cell(0, 0), cell(2, 0)), reachable)
    }

    @Test
    fun reachableIncludesRoomBehindOpenedDoor() {
        val opened = cell(3, 0)
        val reachable = DungeonRoomPathfinder.reachableFrom(corridor(), cell(0, 0)) { it == opened }
        assertEquals(setOf(cell(0, 0), cell(2, 0), cell(4, 0)), reachable)
    }

    @Test
    fun sameRoomTypeAtDisconnectedCellsAreDistinctNodes() {
        val shared = RoomData("Stone", RoomType.NORMAL, emptyList(), 0, 0, 0)
        val grid = grid().apply {
            this[cell(0, 0)] = MapTile.Room(shared, 1)
            this[cell(4, 0)] = MapTile.Room(shared, 1)
        }
        assertNull(DungeonRoomPathfinder.findPath(grid, cell(0, 0), cell(4, 0)))
    }

    @Test
    fun multiCellRoomIsOneNode() {
        val big = MapTile.Room(RoomData("Big", RoomType.NORMAL, emptyList(), 0, 0, 0, "1x2"), 7)
        val grid = corridor().apply {
            set(5, 0, MapTile.Door(DoorType.NORMAL))
            set(6, 0, big)
            set(7, 0, MapTile.Connection(big.data))
            set(8, 0, big)
        }
        val graph = RoomGraph.build(grid)
        assertEquals(graph.canonical(cell(6, 0)), graph.canonical(cell(8, 0)))
        val path = DungeonRoomPathfinder.findPath(grid, cell(4, 0), cell(8, 0), ignoreLocked = true)
        assertNotNull(path)
        assertTrue(path.last().cellIndex in setOf(cell(6, 0), cell(8, 0)))
    }

    @Test
    fun reachableCellsIncludeEveryCellOfMultiCellRoom() {
        val shared = RoomData("Big", RoomType.NORMAL, emptyList(), 0, 0, 0, "1x2")
        val grid = grid().apply {
            this[cell(0, 0)] = MapTile.Room(shared, 1)
            this[cell(1, 0)] = MapTile.Connection(shared)
            this[cell(2, 0)] = MapTile.Room(shared, 1)
        }

        assertEquals(setOf(cell(0, 0), cell(2, 0)), DungeonRoomPathfinder.reachableCellsFrom(grid, cell(0, 0)))
    }
}
