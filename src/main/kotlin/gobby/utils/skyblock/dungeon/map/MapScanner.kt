package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.utils.VecUtils
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.map.MapConstants.CELL_STRIDE
import gobby.utils.skyblock.dungeon.tiles.RoomData
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos

object MapScanner {

    private const val CHUNK_SHIFT = 4
    private const val COORDS_PER_CELL = 2
    private val SEAM_SAMPLES = listOf(-10, -5, 0, 5, 10)

    /**
     * Two-pass scan: rooms first at even grid positions, then gaps resolved by neighbors.
     * Returns true when all chunks are loaded.
     */

    fun scan(grid: Array<MapTile>): Boolean {
        val world = mc.level ?: return false
        var allLoaded = true

        MapGrid.roomCells.forEach { cell ->
            if (!isUnscanned(grid[cell.index])) return@forEach
            when (val scanned = scanRoomCell(world, cell)) {
                ScanResult.ChunkMissing -> allLoaded = false
                ScanResult.NoRoom -> Unit
                is ScanResult.Found -> grid[cell.index] = scanned.room
            }
        }

        RoomInference.infer(grid, { col, row -> seamAt(grid, col, row) }, ::mapShowsRoom)
        RoomInference.fillUnscanned(grid, { col, row -> seamAt(grid, col, row) }, ::mapShowsRoom)

        MapGrid.gapCells.forEach { cell ->
            if (grid[cell.index] !is MapTile.Empty) return@forEach
            grid[cell.index] = resolveGap(grid, cell.col, cell.row) ?: return@forEach
        }

        return allLoaded
    }

    private sealed interface ScanResult {
        data object ChunkMissing : ScanResult
        data object NoRoom : ScanResult
        data class Found(val room: MapTile.Room) : ScanResult
    }

    private fun scanRoomCell(world: ClientLevel, cell: GridCell): ScanResult {
        val xPos = MapGrid.worldX(cell.col)
        val zPos = MapGrid.worldZ(cell.row)
        val chunk = world.chunkSource.getChunk(xPos shr CHUNK_SHIFT, zPos shr CHUNK_SHIFT, false)
        if (chunk == null || chunk.isEmpty) return ScanResult.ChunkMissing
        val position = VecUtils.Vec2(xPos, zPos)
        val height = ScanUtils.getTopLayerOfRoom(position, chunk)
        if (height == 0) return ScanResult.NoRoom
        val core = ScanUtils.getCoreAtHeight(position, height, chunk)
        val roomData = ScanUtils.coreToRoomData[core] ?: return ScanResult.NoRoom
        return ScanResult.Found(MapTile.Room(roomData, core))
    }

    internal fun isUnscanned(tile: MapTile): Boolean =
        tile is MapTile.Empty || (tile is MapTile.Room && (tile.data.name == UNKNOWN_ROOM_NAME || tile.core == MapConstants.UNKNOWN_CORE))

    private fun mapShowsRoom(col: Int, row: Int): Boolean = MapCheckmarks.hasRoomOnMap(col, row) == true

    private fun seamAt(grid: Array<MapTile>, col: Int, row: Int): SeamState {
        if (isDistinctRoomBoundary(grid, col, row)) return SeamState.BLOCKED
        MapCheckmarks.connectedRooms(col, row)?.let { return if (it) SeamState.CONNECTED else SeamState.BLOCKED }
        return seamInWorld(col, row)
    }

    internal fun isDistinctRoomBoundary(grid: Array<MapTile>, col: Int, row: Int): Boolean {
        val rooms = if (col % CELL_STRIDE != 0) {
            listOf(roomAt(grid, col - 1, row), roomAt(grid, col + 1, row))
        } else {
            listOf(roomAt(grid, col, row - 1), roomAt(grid, col, row + 1))
        }
        return rooms.all { it != null } && rooms[0]!!.data !== rooms[1]!!.data
    }

    private fun seamInWorld(col: Int, row: Int): SeamState {
        val world = mc.level ?: return SeamState.UNKNOWN
        val alongZ = col % CELL_STRIDE != 0
        var open = true
        SEAM_SAMPLES.forEach { offset ->
            val x = MapGrid.worldX(col) + if (alongZ) 0 else offset
            val z = MapGrid.worldZ(row) + if (alongZ) offset else 0
            if (world.chunkSource.getChunk(x shr CHUNK_SHIFT, z shr CHUNK_SHIFT, false) == null) return SeamState.UNKNOWN
            if (!DungeonDoorDetector.isOpenFloor(x, z) { world.getBlockState(it) }) open = false
        }
        return if (open) SeamState.CONNECTED else SeamState.BLOCKED
    }

    private fun resolveGap(grid: Array<MapTile>, col: Int, row: Int): MapTile? {
        val colOdd = isOdd(col)
        val rowOdd = isOdd(row)
        return when {
            colOdd && rowOdd -> resolveDiagonal(grid, col, row)
            colOdd -> resolveNeighbors(roomAt(grid, col - 1, row), roomAt(grid, col + 1, row), col, row)
            else -> resolveNeighbors(roomAt(grid, col, row - 1), roomAt(grid, col, row + 1), col, row)
        }
    }

    private fun isOdd(value: Int): Boolean = value % CELL_STRIDE != 0

    private fun roomAt(grid: Array<MapTile>, col: Int, row: Int): MapTile.Room? =
        if (!MapGrid.inRange(col, row)) null else grid.getOrNull(MapGrid.index(col, row)) as? MapTile.Room

    private fun resolveDiagonal(grid: Array<MapTile>, col: Int, row: Int): MapTile? {
        val corners = listOf(
            roomAt(grid, col - 1, row - 1), roomAt(grid, col + 1, row - 1),
            roomAt(grid, col - 1, row + 1), roomAt(grid, col + 1, row + 1)
        )
        val first = corners.firstOrNull() ?: return null
        if (first.data.name == UNKNOWN_ROOM_NAME) return null
        val shared = corners.all { it != null && it.data === first.data }
        return if (shared) MapTile.Connection(first.data) else null
    }

    internal fun resolveNeighbors(roomA: MapTile.Room?, roomB: MapTile.Room?, col: Int, row: Int): MapTile? {
        if (roomA == null && roomB == null) return null
        if (roomA != null && roomB != null && roomA.data === roomB.data) return MapTile.Connection(roomA.data)
        return detectDoor(col, row)
    }

    private fun detectDoor(col: Int, row: Int): MapTile.Door? {
        val world = mc.level ?: return null
        val x = MapGrid.worldX(col)
        val z = MapGrid.worldZ(row)
        if (world.chunkSource.getChunk(x shr CHUNK_SHIFT, z shr CHUNK_SHIFT, false) == null) return null
        return DungeonDoorDetector.detect(x, z) { world.getBlockState(it) }
    }
}
