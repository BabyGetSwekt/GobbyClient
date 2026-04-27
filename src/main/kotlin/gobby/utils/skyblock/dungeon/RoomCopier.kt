package gobby.utils.skyblock.dungeon

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.DungeonMap
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.map.MapConstants.HALF_ROOM
import gobby.utils.skyblock.dungeon.map.MapConstants.START_X
import gobby.utils.skyblock.dungeon.map.MapConstants.START_Z
import gobby.utils.copy.BlockStateCodec
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.util.math.BlockPos
import java.io.File

object RoomCopier {

    private const val ROOM_SIZE = 32
    private const val SCAN_Y_MIN = 65
    private const val SCAN_Y_MAX = 160

    private val roomsDir = File("./config/gobbyclientFabric/rooms").apply { mkdirs() }

    fun copyCurrentRoom() {
        val player = mc.player ?: return errorMessage("No player")
        val world = mc.world ?: return errorMessage("No world")

        val col = (player.blockPos.x - START_X) / HALF_ROOM
        val row = (player.blockPos.z - START_Z) / HALF_ROOM
        if (col !in 0 until GRID_SIZE || row !in 0 until GRID_SIZE) return errorMessage("Not on dungeon grid")

        val roomData = when (val tile = DungeonMap.grid[row * GRID_SIZE + col]) {
            is MapTile.Room -> tile.data
            is MapTile.Connection -> tile.data
            else -> return errorMessage("Current tile is not a room")
        }

        val cells = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until GRID_SIZE step 2) for (c in 0 until GRID_SIZE step 2) {
            val t = DungeonMap.grid[r * GRID_SIZE + c]
            if (t is MapTile.Room && t.data === roomData) cells.add(c to r)
        }
        if (cells.isEmpty()) return errorMessage("Could not resolve room cells")

        val minCol = cells.minOf { it.first }
        val maxCol = cells.maxOf { it.first }
        val minRow = cells.minOf { it.second }
        val maxRow = cells.maxOf { it.second }
        val originX = START_X + minCol * HALF_ROOM
        val originZ = START_Z + minRow * HALF_ROOM
        val width = (maxCol - minCol) / 2 * ROOM_SIZE + ROOM_SIZE - 1
        val length = (maxRow - minRow) / 2 * ROOM_SIZE + ROOM_SIZE - 1

        val missingChunks = mutableListOf<String>()
        for (cx in (originX shr 4)..((originX + width) shr 4))
            for (cz in (originZ shr 4)..((originZ + length) shr 4))
                if (!world.chunkManager.isChunkLoaded(cx, cz)) missingChunks.add("$cx,$cz")
        if (missingChunks.isNotEmpty()) return errorMessage("Chunks not loaded (walk closer): ${missingChunks.joinToString(" ")}")

        val (bottom, height) = findYBounds(originX, originZ, width, length)
        if (height <= 0) return errorMessage("Empty column under room")

        val palette = mutableListOf("minecraft:air")
        val paletteIndex = HashMap<String, Int>().apply { put("minecraft:air", 0) }
        val runs = mutableListOf<Int>()
        var currentIdx = 0
        var currentLen = 0

        for (y in 0 until height) for (z in 0 until length) for (x in 0 until width) {
            val state = world.getBlockState(BlockPos(originX + x, bottom + y, originZ + z))
            val key = if (state.isAir) "minecraft:air" else BlockStateCodec.encode(state)
            val idx = paletteIndex.getOrPut(key) { palette.add(key); palette.size - 1 }
            if (idx == currentIdx) currentLen++ else {
                if (currentLen > 0) { runs.add(currentIdx); runs.add(currentLen) }
                currentIdx = idx
                currentLen = 1
            }
        }
        if (currentLen > 0) { runs.add(currentIdx); runs.add(currentLen) }

        val shape = roomData.shape
        val type = roomData.type.name.lowercase()
        val file = File(roomsDir, "${sanitize(roomData.name)}.json")
        file.writeText(buildJson(roomData.name, shape, type, bottom, width, length, height, palette, runs))

        modMessage("§aSaved §f${file.name} §a($shape $type) §7| §fcells=${cells.size} §fpalette=${palette.size} §fruns=${runs.size / 2} §f${file.length() / 1024}KB")
    }

    private fun findYBounds(originX: Int, originZ: Int, width: Int, length: Int): Pair<Int, Int> {
        val world = mc.world ?: return 0 to 0
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (x in 0 until width step 2) for (z in 0 until length step 2) {
            for (y in SCAN_Y_MIN..SCAN_Y_MAX) {
                if (!world.getBlockState(BlockPos(originX + x, y, originZ + z)).isAir) {
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (minY == Int.MAX_VALUE) return 0 to 0
        return minY to (maxY - minY + 1)
    }

    private fun sanitize(s: String): String = s.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "Room" }

    private fun buildJson(name: String, shape: String, type: String, bottom: Int, width: Int, length: Int, height: Int, palette: List<String>, runs: List<Int>): String {
        val sb = StringBuilder()
        sb.append("{\"n\":\"").append(name).append("\",\"s\":\"").append(shape).append("\",\"t\":\"").append(type)
            .append("\",\"b\":").append(bottom).append(",\"w\":").append(width).append(",\"l\":").append(length)
            .append(",\"h\":").append(height).append(",\"p\":[")
        palette.forEachIndexed { i, s -> if (i > 0) sb.append(','); sb.append('"').append(s).append('"') }
        sb.append("],\"d\":[")
        runs.forEachIndexed { i, v -> if (i > 0) sb.append(','); sb.append(v) }
        sb.append("]}")
        return sb.toString()
    }
}
