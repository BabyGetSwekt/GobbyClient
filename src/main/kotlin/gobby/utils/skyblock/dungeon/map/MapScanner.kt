package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.utils.VecUtils
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.map.MapConstants.HALF_ROOM
import gobby.utils.skyblock.dungeon.map.MapConstants.START_X
import gobby.utils.skyblock.dungeon.map.MapConstants.START_Z
import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos

object MapScanner {

    /** Runs only inference + gap resolution on an existing grid (for testing). */
    fun runInference(grid: Array<MapTile>) {
        inferRooms(grid)
        // Resolve gaps
        for (col in 0 until GRID_SIZE) {
            for (row in 0 until GRID_SIZE) {
                if ((col and 1 == 0) && (row and 1 == 0)) continue
                val index = row * GRID_SIZE + col
                if (grid[index] !is MapTile.Empty) continue
                grid[index] = resolveGapNoWorld(grid, col, row) ?: continue
            }
        }
    }

    /** Resolves gaps without world access (for test mode — connections only, no doors). */
    private fun resolveGapNoWorld(grid: Array<MapTile>, col: Int, row: Int): MapTile? {
        val colOdd = col and 1 == 1
        val rowOdd = row and 1 == 1
        return when {
            colOdd && rowOdd -> {
                val tl = grid.getOrNull((row - 1) * GRID_SIZE + (col - 1)) as? MapTile.Room
                val tr = grid.getOrNull((row - 1) * GRID_SIZE + (col + 1)) as? MapTile.Room
                val bl = grid.getOrNull((row + 1) * GRID_SIZE + (col - 1)) as? MapTile.Room
                val br = grid.getOrNull((row + 1) * GRID_SIZE + (col + 1)) as? MapTile.Room
                if (tl != null && tr != null && bl != null && br != null &&
                    tl.data === tr.data && tl.data === bl.data && tl.data === br.data)
                    MapTile.Connection(tl.data) else null
            }
            colOdd -> {
                val left = grid.getOrNull(row * GRID_SIZE + (col - 1)) as? MapTile.Room
                val right = grid.getOrNull(row * GRID_SIZE + (col + 1)) as? MapTile.Room
                if (left != null && right != null && left.data === right.data) MapTile.Connection(left.data) else null
            }
            else -> {
                val top = grid.getOrNull((row - 1) * GRID_SIZE + col) as? MapTile.Room
                val bottom = grid.getOrNull((row + 1) * GRID_SIZE + col) as? MapTile.Room
                if (top != null && bottom != null && top.data === bottom.data) MapTile.Connection(top.data) else null
            }
        }
    }

    /**
     * Two-pass scan: rooms first at even grid positions, then gaps resolved by neighbors.
     * Returns true when all chunks are loaded.
     */
    fun scan(grid: Array<MapTile>): Boolean {
        val world = mc.world ?: return false
        var allLoaded = true

        for (col in 0 until GRID_SIZE step 2) {
            for (row in 0 until GRID_SIZE step 2) {
                val index = row * GRID_SIZE + col
                if (grid[index] !is MapTile.Empty) continue

                val xPos = START_X + col * HALF_ROOM
                val zPos = START_Z + row * HALF_ROOM
                val chunk = world.getChunk(xPos shr 4, zPos shr 4)
                if (chunk.isEmpty) { allLoaded = false; continue }

                val height = ScanUtils.getTopLayerOfRoom(VecUtils.Vec2(xPos, zPos), chunk)
                if (height == 0) continue

                val core = ScanUtils.getCoreAtHeight(VecUtils.Vec2(xPos, zPos), height, chunk)
                val roomData = ScanUtils.coreToRoomData[core] ?: continue
                grid[index] = MapTile.Room(roomData, core)
            }
        }

        inferRooms(grid)

        for (col in 0 until GRID_SIZE) {
            for (row in 0 until GRID_SIZE) {
                if ((col and 1 == 0) && (row and 1 == 0)) continue
                val index = row * GRID_SIZE + col
                if (grid[index] !is MapTile.Empty) continue
                grid[index] = resolveGap(grid, col, row) ?: continue
            }
        }

        return allLoaded
    }

    /**
     * Infers missing cells for multi-cell rooms by elimination.
     * For each scanned room cell, generates all possible arrangements for its shape,
     * eliminates arrangements where any position has a different room,
     * and fills in empty cells if only 1 valid arrangement remains.
     * Loops until no more changes (cascading inference).
     */
    private fun inferRooms(grid: Array<MapTile>) {
        var changed = true
        while (changed) {
            changed = false
            for (col in 0 until GRID_SIZE step 2) {
                for (row in 0 until GRID_SIZE step 2) {
                    val room = grid[row * GRID_SIZE + col] as? MapTile.Room ?: continue
                    val arrangements = getArrangements(room.data.shape, col, row) ?: continue

                    // Filter: valid position AND doesn't block any other multi-cell room
                    val valid = arrangements.filter { block ->
                        isValidBlock(grid, room.data, block) && !wouldBlockOtherRoom(grid, room.data, block)
                    }
                    if (valid.size == 1) {
                        val block = valid[0]
                        for (i in 0 until block.size step 2) {
                            val idx = block[i + 1] * GRID_SIZE + block[i]
                            if (grid[idx] is MapTile.Empty) {
                                grid[idx] = MapTile.Room(room.data, 0)
                                changed = true
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Lookahead: tentatively fills a block, then checks if any other scanned
     * multi-cell room would have 0 valid arrangements left. If so, this
     * arrangement is invalid — it would block another room.
     */
    private fun wouldBlockOtherRoom(grid: Array<MapTile>, data: RoomData, block: IntArray): Boolean {
        val backups = mutableListOf<Pair<Int, MapTile>>()
        for (i in 0 until block.size step 2) {
            val idx = block[i + 1] * GRID_SIZE + block[i]
            if (grid[idx] is MapTile.Empty) {
                backups.add(idx to grid[idx])
                grid[idx] = MapTile.Room(data, 0)
            }
        }

        var blocked = false
        outer@ for (c in 0 until GRID_SIZE step 2) {
            for (r in 0 until GRID_SIZE step 2) {
                val other = grid[r * GRID_SIZE + c] as? MapTile.Room ?: continue
                if (other.data === data) continue // skip cells of the same room
                val otherArrangements = getArrangements(other.data.shape, c, r) ?: continue
                if (otherArrangements.none { isValidBlock(grid, other.data, it) }) {
                    blocked = true
                    break@outer
                }
            }
        }

        // Undo tentative fill
        for ((idx, tile) in backups) { grid[idx] = tile }
        return blocked
    }

    /** Generates all possible arrangements for a room shape at a given cell position. */
    private fun getArrangements(shape: String, col: Int, row: Int): List<IntArray>? {
        return when (shape) {
            "1x2" -> buildLinearArrangements(2, col, row)
            "1x3" -> buildLinearArrangements(3, col, row)
            "1x4" -> buildLinearArrangements(4, col, row)
            "2x2" -> listOf(
                intArrayOf(col, row, col + 2, row, col, row + 2, col + 2, row + 2),
                intArrayOf(col - 2, row, col, row, col - 2, row + 2, col, row + 2),
                intArrayOf(col, row - 2, col + 2, row - 2, col, row, col + 2, row),
                intArrayOf(col - 2, row - 2, col, row - 2, col - 2, row, col, row),
            )
            "L" -> buildLArrangements(col, row)
            else -> null // 1x1: no inference
        }
    }

    /** Builds all horizontal + vertical arrangements for a 1xN linear room. */
    private fun buildLinearArrangements(size: Int, col: Int, row: Int): List<IntArray> {
        val list = mutableListOf<IntArray>()
        for (off in 0 until size) {
            val sc = col - off * 2
            val arr = IntArray(size * 2)
            for (i in 0 until size) { arr[i * 2] = sc + i * 2; arr[i * 2 + 1] = row }
            list.add(arr)
        }

        for (off in 0 until size) {
            val sr = row - off * 2
            val arr = IntArray(size * 2)
            for (i in 0 until size) { arr[i * 2] = col; arr[i * 2 + 1] = sr + i * 2 }
            list.add(arr)
        }
        return list
    }

    /** Builds all 12 possible L-shape arrangements for a cell at (col, row). */
    private fun buildLArrangements(col: Int, row: Int): List<IntArray> {
        val list = mutableListOf<IntArray>()
        list.add(intArrayOf(col, row, col + 2, row, col, row + 2))       // right + down
        list.add(intArrayOf(col, row, col + 2, row, col, row - 2))       // right + up
        list.add(intArrayOf(col, row, col - 2, row, col, row + 2))       // left + down
        list.add(intArrayOf(col, row, col - 2, row, col, row - 2))       // left + up

        list.add(intArrayOf(col - 2, row, col, row, col - 2, row + 2))   // corner left, arm down
        list.add(intArrayOf(col - 2, row, col, row, col - 2, row - 2))   // corner left, arm up
        list.add(intArrayOf(col + 2, row, col, row, col + 2, row + 2))   // corner right, arm down
        list.add(intArrayOf(col + 2, row, col, row, col + 2, row - 2))   // corner right, arm up

        list.add(intArrayOf(col, row - 2, col + 2, row - 2, col, row))   // corner above, arm right
        list.add(intArrayOf(col, row - 2, col - 2, row - 2, col, row))   // corner above, arm left
        list.add(intArrayOf(col, row + 2, col + 2, row + 2, col, row))   // corner below, arm right
        list.add(intArrayOf(col, row + 2, col - 2, row + 2, col, row))   // corner below, arm left
        return list
    }

    private fun isValidBlock(grid: Array<MapTile>, data: RoomData, block: IntArray): Boolean {
        for (i in 0 until block.size step 2) {
            val c = block[i]; val r = block[i + 1]
            if (c < 0 || c >= GRID_SIZE || r < 0 || r >= GRID_SIZE) return false
            val tile = grid[r * GRID_SIZE + c]
            if (tile is MapTile.Room && tile.data !== data) return false
        }
        return true
    }

    private fun resolveGap(grid: Array<MapTile>, col: Int, row: Int): MapTile? {
        val colOdd = col and 1 == 1
        val rowOdd = row and 1 == 1

        return when {
            colOdd && rowOdd -> {
                val tl = grid.getOrNull((row - 1) * GRID_SIZE + (col - 1)) as? MapTile.Room
                val tr = grid.getOrNull((row - 1) * GRID_SIZE + (col + 1)) as? MapTile.Room
                val bl = grid.getOrNull((row + 1) * GRID_SIZE + (col - 1)) as? MapTile.Room
                val br = grid.getOrNull((row + 1) * GRID_SIZE + (col + 1)) as? MapTile.Room
                if (tl != null && tr != null && bl != null && br != null &&
                    tl.data === tr.data && tl.data === bl.data && tl.data === br.data)
                    MapTile.Connection(tl.data) else null
            }

            colOdd -> resolveNeighbors(
                grid.getOrNull(row * GRID_SIZE + (col - 1)),
                grid.getOrNull(row * GRID_SIZE + (col + 1)),
                col, row
            )

            else -> resolveNeighbors(
                grid.getOrNull((row - 1) * GRID_SIZE + col),
                grid.getOrNull((row + 1) * GRID_SIZE + col),
                col, row
            )
        }
    }

    private fun resolveNeighbors(a: MapTile?, b: MapTile?, col: Int, row: Int): MapTile? {
        val roomA = a as? MapTile.Room
        val roomB = b as? MapTile.Room

        if (roomA == null && roomB == null) return null
        if (roomA == null || roomB == null) return null

        if (roomA.data === roomB.data) return MapTile.Connection(roomA.data)

        return detectDoor(col, row, roomA, roomB)
    }

    /**
     * Checks if there's an actual door/passage at this gap position.
     * Like Devonian: only creates a door when there's a ceiling (height > 0) lower than room height (< 85).
     * No ceiling or full-height wall = no passage = null.
     */
    private fun detectDoor(col: Int, row: Int, a: MapTile.Room, b: MapTile.Room): MapTile.Door? {
        val world = mc.world ?: return null
        val x = START_X + col * HALF_ROOM
        val z = START_Z + row * HALF_ROOM

        val chunk = world.getChunk(x shr 4, z shr 4)
        val height = ScanUtils.getTopLayerOfRoom(VecUtils.Vec2(x, z), chunk)

        if (height == 0 || height >= 85) return null

        val block = world.getBlockState(BlockPos(x, 69, z)).block
        val type = when {
            a.data.type == RoomType.ENTRANCE || b.data.type == RoomType.ENTRANCE -> DoorType.ENTRANCE
            block == Blocks.COAL_BLOCK -> DoorType.WITHER
            block == Blocks.RED_TERRACOTTA -> DoorType.BLOOD
            else -> DoorType.NORMAL
        }
        return MapTile.Door(type)
    }
}
