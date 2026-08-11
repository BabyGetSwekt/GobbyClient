package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.MapItem
import net.minecraft.world.item.Items
import net.minecraft.world.level.saveddata.maps.MapItemSavedData

object MapCheckmarks {

    private const val MAP_SIZE = 128
    private const val HOTBAR_SIZE = 9
    private const val EMPTY_COLOR: Byte = 0
    private const val ROOM_GREEN_MIN = 29
    private const val ROOM_GREEN_MAX = 31
    private const val ROOM_UNOPENED: Byte = 85
    private const val ROOM_SPACING = 4
    private const val ROOM_SIZE_MIN = 6
    private const val CHECK_WHITE_MIN = 33
    private const val CHECK_WHITE_MAX = 35
    private const val CHECK_GREEN_MIN = 29
    private const val CHECK_GREEN_MAX = 32
    private const val CHECK_FAILED_MIN = 17
    private const val CHECK_FAILED_MAX = 19
    private const val CHECK_UNKNOWN_MIN = 118
    private const val CHECK_UNKNOWN_MAX = 120

    private var mapOffsetX = -1
    private var mapOffsetZ = -1
    private var roomPixelSize = -1
    private var roomGap = -1
    private var mapRotation = MapRotation.NONE
    private var entranceCol = -1
    private var entranceRow = -1

    fun reset() {
        mapOffsetX = -1
        mapOffsetZ = -1
        roomPixelSize = -1
        roomGap = -1
        mapRotation = MapRotation.NONE
        entranceCol = -1
        entranceRow = -1
    }

    fun update(grid: Array<MapTile>, checkmarks: Array<MapCheckmark>, discovered: BooleanArray) {
        val player = mc.player ?: return
        val mapState = findMapState(player) ?: return
        val colors = mapState.colors.clone()

        if (mapOffsetX < 0 && !scanMapDimensions(grid, colors)) return

        roomCells(grid).forEach { index -> updateRoom(index, grid, colors, checkmarks, discovered) }
    }

    private fun MapCheckmark.priority(): Int = when (this) {
        MapCheckmark.NONE -> 0
        MapCheckmark.UNKNOWN -> 1
        MapCheckmark.WHITE -> 2
        MapCheckmark.GREEN -> 3
        MapCheckmark.FAILED -> 4
    }

    private fun roomCells(grid: Array<MapTile>): List<Int> =
        (0 until GRID_SIZE step 2).flatMap { row ->
            (0 until GRID_SIZE step 2).mapNotNull { col ->
                (row * GRID_SIZE + col).takeIf { grid[it] is MapTile.Room }
            }
        }

    private fun updateRoom(index: Int, grid: Array<MapTile>, colors: ByteArray, checkmarks: Array<MapCheckmark>, discovered: BooleanArray) {
        if (grid[index] !is MapTile.Room) return
        val (x, z) = mapPosition(index)
        val origin = colors.getOrNull(x + z * MAP_SIZE)
        val center = colors.getOrNull(x + roomPixelSize / 2 + (z + roomPixelSize / 2) * MAP_SIZE)
        val roomColor = origin?.takeUnless { it == EMPTY_COLOR } ?: center ?: return
        if (isExplored(origin)) discovered[index] = true
        val detected = detectCheckmark(colors, x, z, roomColor)
        if (detected.priority() > checkmarks[index].priority()) checkmarks[index] = detected
    }

    private fun isExplored(byte: Byte?): Boolean = byte != null && byte != EMPTY_COLOR && byte != ROOM_UNOPENED

    private fun detectCheckmark(colors: ByteArray, x: Int, z: Int, roomColor: Byte): MapCheckmark =
        (0 until roomPixelSize).flatMap { dx ->
            (0 until roomPixelSize).mapNotNull { dz -> colors.getOrNull(x + dx + (z + dz) * MAP_SIZE) }
        }.mapNotNull { color ->
            when (color.toInt()) {
                in CHECK_WHITE_MIN..CHECK_WHITE_MAX -> MapCheckmark.WHITE
                in CHECK_GREEN_MIN..CHECK_GREEN_MAX -> if (roomColor.toInt() in ROOM_GREEN_MIN..ROOM_GREEN_MAX) null else MapCheckmark.GREEN
                in CHECK_FAILED_MIN..CHECK_FAILED_MAX -> MapCheckmark.FAILED
                in CHECK_UNKNOWN_MIN..CHECK_UNKNOWN_MAX -> MapCheckmark.UNKNOWN
                else -> null
            }
        }.maxByOrNull { it.priority() } ?: MapCheckmark.NONE

    private fun scanMapDimensions(grid: Array<MapTile>, colors: ByteArray): Boolean {
        val entrance = roomCells(grid).firstOrNull { (grid[it] as MapTile.Room).data.type == RoomType.ENTRANCE } ?: return false
        entranceCol = entrance % GRID_SIZE / 2
        entranceRow = entrance / GRID_SIZE / 2
        val candidate = colors.indices.asSequence()
            .filter { colors[it].toInt() in ROOM_GREEN_MIN..ROOM_GREEN_MAX }
            .map { roomBounds(colors, it) }
            .filter { it.width >= ROOM_SIZE_MIN && it.width == it.height }
            .fold<RoomBounds, RoomBounds?>(null) { largest, current ->
                if (largest == null || current.width > largest.width) current else largest
            } ?: return false
        roomPixelSize = candidate.width
        roomGap = roomPixelSize + ROOM_SPACING
        mapOffsetX = candidate.left
        mapOffsetZ = candidate.top
        mapRotation = findMapRotation(grid, colors)
        return true
    }

    private fun mapPosition(index: Int): Pair<Int, Int> {
        val col = index % GRID_SIZE / 2
        val row = index / GRID_SIZE / 2
        val relativeCol = col - entranceCol
        val relativeRow = row - entranceRow
        val (mapCol, mapRow) = mapRotation.transform(relativeCol, relativeRow)
        return mapOffsetX + mapCol * roomGap to mapOffsetZ + mapRow * roomGap
    }

    private fun findMapRotation(grid: Array<MapTile>, colors: ByteArray): MapRotation =
        MapRotation.entries.maxByOrNull { rotation ->
            roomCells(grid).count { index ->
                val col = index % GRID_SIZE / 2 - entranceCol
                val row = index / GRID_SIZE / 2 - entranceRow
                val (mapCol, mapRow) = rotation.transform(col, row)
                val x = mapOffsetX + mapCol * roomGap
                val z = mapOffsetZ + mapRow * roomGap
                colors.getOrNull(x + z * MAP_SIZE)?.let { it != EMPTY_COLOR } == true
            }
        } ?: MapRotation.NONE

    private fun roomBounds(colors: ByteArray, index: Int): RoomBounds {
        val color = colors[index]
        var left = index % MAP_SIZE
        var right = left
        var topIndex = index
        var bottomIndex = index
        val rowStart = index - left
        while (left > 0 && colors[index - (index % MAP_SIZE) + left - 1] == color) left--
        while (right < MAP_SIZE - 1 && colors[rowStart + right + 1] == color) right++
        while (topIndex >= MAP_SIZE && colors[topIndex - MAP_SIZE] == color) topIndex -= MAP_SIZE
        while (bottomIndex + MAP_SIZE < colors.size && colors[bottomIndex + MAP_SIZE] == color) bottomIndex += MAP_SIZE
        return RoomBounds(left, topIndex / MAP_SIZE, right - left + 1, bottomIndex / MAP_SIZE - topIndex / MAP_SIZE + 1)
    }

    fun doorByte(col: Int, row: Int): Byte? {
        val state = mc.player?.let { findMapState(it) } ?: return null
        if (mapOffsetX < 0) return null
        val mjx = mapOffsetX + (col / 2 - entranceCol) * roomGap + (col and 1) * roomPixelSize
        val mjz = mapOffsetZ + (row / 2 - entranceRow) * roomGap + (row and 1) * roomPixelSize
        val mdx = mjx + (row and 1) * roomPixelSize / 2
        val mdz = mjz + (col and 1) * roomPixelSize / 2
        return state.colors.getOrNull(mdx + mdz * MAP_SIZE)
    }

    fun debugInfo(): String {
        val mapFound = mc.player?.let { findMapState(it) } != null
        return "mapFound=$mapFound offset=($mapOffsetX,$mapOffsetZ) gap=$roomGap size=$roomPixelSize rot=$mapRotation entrance=($entranceCol,$entranceRow)"
    }

    fun dumpMapGrid(): List<String> {
        val state = mc.player?.let { findMapState(it) } ?: return listOf("no-map")
        val colors = state.colors
        if (roomGap < 1) return listOf("no-dims")
        val phaseX = ((mapOffsetX % roomGap) + roomGap) % roomGap
        val phaseZ = ((mapOffsetZ % roomGap) + roomGap) % roomGap
        val xs = (phaseX until MAP_SIZE step roomGap).toList()
        return (phaseZ until MAP_SIZE step roomGap).map { z ->
            "z=$z: " + xs.joinToString(",") { x -> "${colors.getOrNull(x + z * MAP_SIZE)?.toInt() ?: -9}" }
        }
    }

    fun dumpRooms(grid: Array<MapTile>, discovered: BooleanArray): List<String> {
        val state = mc.player?.let { findMapState(it) } ?: return listOf("no-map")
        val colors = state.colors
        if (mapOffsetX < 0) return listOf("no-dims")
        return roomCells(grid).map { index ->
            val name = (grid[index] as MapTile.Room).data.name
            val (x, z) = mapPosition(index)
            val origin = colors.getOrNull(x + z * MAP_SIZE)
            val center = colors.getOrNull(x + roomPixelSize / 2 + (z + roomPixelSize / 2) * MAP_SIZE)
            "$name col=${index % GRID_SIZE / 2} row=${index / GRID_SIZE / 2} px=($x,$z) origin=$origin center=$center disc=${discovered.getOrElse(index) { false }}"
        }
    }

    private fun findMapState(player: Player): MapItemSavedData? {
        val world = mc.level ?: return null
        for (slot in 0 until HOTBAR_SIZE) {
            val stack = player.inventory.getItem(slot)
            if (stack.item == Items.FILLED_MAP) {
                val state = MapItem.getSavedData(stack, world)
                if (state != null) return state
            }
        }
        return null
    }
}

private data class RoomBounds(val left: Int, val top: Int, val width: Int, val height: Int)

private enum class MapRotation {
    NONE,
    CLOCKWISE_90,
    HALF_TURN,
    CLOCKWISE_270;

    fun transform(col: Int, row: Int): Pair<Int, Int> = when (this) {
        NONE -> col to row
        CLOCKWISE_90 -> -row to col
        HALF_TURN -> -col to -row
        CLOCKWISE_270 -> row to -col
    }
}
