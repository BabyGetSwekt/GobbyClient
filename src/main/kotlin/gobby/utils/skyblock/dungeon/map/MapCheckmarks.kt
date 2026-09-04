package gobby.utils.skyblock.dungeon.map

import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.tiles.RoomType

object MapCheckmarks {

    private const val MAP_SIZE = 128
    private const val EMPTY_COLOR: Byte = 0
    private const val ROOM_GREEN_MIN = 29
    private const val ROOM_GREEN_MAX = 31
    private const val ROOM_UNOPENED: Byte = 85
    private const val ROOM_SPACING = 4
    private val CONNECTOR_EDGE_INSET = listOf(1, -2)
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
        val colors = DungeonMapSource.colors ?: return

        if (mapOffsetX < 0 && !scanMapDimensions(grid, colors)) return

        roomCells(grid).forEach { index -> updateRoom(index, grid, colors, checkmarks, discovered) }
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
        if (detected.ordinal > checkmarks[index].ordinal) checkmarks[index] = detected
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
        }.maxByOrNull { it.ordinal } ?: MapCheckmark.NONE

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

    fun roomCoordsFromMapPixel(pixelX: Double, pixelZ: Double): Pair<Double, Double>? {
        if (mapOffsetX < 0 || roomGap <= 0) return null
        val halfRoom = roomPixelSize / 2.0
        val (relCol, relRow) = mapRotation.invert(
            (pixelX - halfRoom - mapOffsetX) / roomGap,
            (pixelZ - halfRoom - mapOffsetZ) / roomGap
        )
        return relCol + entranceCol to relRow + entranceRow
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

    fun gapJoined(col: Int, row: Int): Boolean? {
        val colors = DungeonMapSource.colors ?: return null
        if (mapOffsetX < 0) return null
        val joined = colors.pixel(originX(col), originZ(row)) ?: return null
        val door = colors.pixel(
            originX(col) + (row and 1) * roomPixelSize / 2,
            originZ(row) + (col and 1) * roomPixelSize / 2
        ) ?: return null
        if (door == EMPTY_COLOR) return null
        return joined == door
    }

    fun doorByte(col: Int, row: Int): Byte? {
        val colors = DungeonMapSource.colors ?: return null
        if (mapOffsetX < 0) return null
        return colors.pixel(originX(col) + (row and 1) * roomPixelSize / 2, originZ(row) + (col and 1) * roomPixelSize / 2)
    }

    private fun originX(col: Int): Int = mapOffsetX + (col / 2 - entranceCol) * roomGap + (col and 1) * roomPixelSize

    private fun originZ(row: Int): Int = mapOffsetZ + (row / 2 - entranceRow) * roomGap + (row and 1) * roomPixelSize

    private fun ByteArray.pixel(x: Int, z: Int): Byte? =
        if (x !in 0 until MAP_SIZE || z !in 0 until MAP_SIZE) null else getOrNull(x + z * MAP_SIZE)

    fun connectedRooms(col: Int, row: Int): Boolean? {
        if (mapOffsetX < 0) return null
        val alongZ = col % 2 != 0
        val first = roomByte(if (alongZ) col - 1 else col, if (alongZ) row else row - 1) ?: return null
        val second = roomByte(if (alongZ) col + 1 else col, if (alongZ) row else row + 1) ?: return null
        if (first == EMPTY_COLOR || second == EMPTY_COLOR || first != second) return null
        return connectorIsFilled(col, row, first)
    }

    private fun connectorIsFilled(col: Int, row: Int, roomColor: Byte): Boolean? {
        val colors = DungeonMapSource.colors ?: return null
        val alongZ = col % 2 != 0
        val gapX = originX(col)
        val gapZ = originZ(row)
        return CONNECTOR_EDGE_INSET.all { inset ->
            val offset = if (inset < 0) roomPixelSize + inset else inset
            val px = if (alongZ) gapX else gapX + offset
            val pz = if (alongZ) gapZ + offset else gapZ
            colors.pixel(px, pz) == roomColor
        }
    }

    fun hasRoomOnMap(col: Int, row: Int): Boolean? {
        if (mapOffsetX < 0) return null
        return roomByte(col, row)?.let { it != EMPTY_COLOR }
    }

    private fun roomByte(col: Int, row: Int): Byte? {
        val colors = DungeonMapSource.colors ?: return null
        return colors.pixel(originX(col) + roomPixelSize / 2, originZ(row) + roomPixelSize / 2)
    }

    fun debugInfo(): String {
        val mapFound = DungeonMapSource.colors != null
        return "mapFound=$mapFound offset=($mapOffsetX,$mapOffsetZ) gap=$roomGap size=$roomPixelSize rot=$mapRotation entrance=($entranceCol,$entranceRow)"
    }

    fun dumpMapGrid(): List<String> {
        val colors = DungeonMapSource.colors ?: return listOf("no-map")
        if (roomGap < 1) return listOf("no-dims")
        val phaseX = ((mapOffsetX % roomGap) + roomGap) % roomGap
        val phaseZ = ((mapOffsetZ % roomGap) + roomGap) % roomGap
        val xs = (phaseX until MAP_SIZE step roomGap).toList()
        return (phaseZ until MAP_SIZE step roomGap).map { z ->
            "z=$z: " + xs.joinToString(",") { x -> "${colors.getOrNull(x + z * MAP_SIZE)?.toInt() ?: -9}" }
        }
    }

    fun dumpRooms(grid: Array<MapTile>, discovered: BooleanArray): List<String> {
        val colors = DungeonMapSource.colors ?: return listOf("no-map")
        if (mapOffsetX < 0) return listOf("no-dims")
        return roomCells(grid).map { index ->
            val name = (grid[index] as MapTile.Room).data.name
            val (x, z) = mapPosition(index)
            val origin = colors.getOrNull(x + z * MAP_SIZE)
            val center = colors.getOrNull(x + roomPixelSize / 2 + (z + roomPixelSize / 2) * MAP_SIZE)
            "$name col=${index % GRID_SIZE / 2} row=${index / GRID_SIZE / 2} px=($x,$z) origin=$origin center=$center disc=${discovered.getOrElse(index) { false }}"
        }
    }

}

private data class RoomBounds(val left: Int, val top: Int, val width: Int, val height: Int)

internal enum class MapRotation {
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

    fun invert(mapCol: Double, mapRow: Double): Pair<Double, Double> = when (this) {
        NONE -> mapCol to mapRow
        CLOCKWISE_90 -> mapRow to -mapCol
        HALF_TURN -> -mapCol to -mapRow
        CLOCKWISE_270 -> -mapRow to mapCol
    }
}
