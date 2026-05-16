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
import gobby.utils.copy.EntityCodec
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.world.entity.player.Player
import net.minecraft.core.BlockPos
import org.slf4j.LoggerFactory
import java.io.File

object RoomCopier {

    private const val ROOM_SIZE = 32
    private const val SCAN_Y_MIN = 0
    private const val SCAN_Y_MAX = 160

    private val roomsDir = File("./config/gobbyclientFabric/rooms").apply { mkdirs() }
    private val LOGGER = LoggerFactory.getLogger("RoomCopier")

    fun copyCurrentRoom() {
        val player = mc.player ?: return errorMessage("No player")
        val world = mc.level ?: return errorMessage("No world")

        val rawCol = (player.blockPosition().x - START_X) / HALF_ROOM
        val rawRow = (player.blockPosition().z - START_Z) / HALF_ROOM
        if (rawCol !in 0 until GRID_SIZE || rawRow !in 0 until GRID_SIZE) return errorMessage("Not on dungeon grid")

        val col = (rawCol / 2) * 2
        val row = (rawRow / 2) * 2
        val roomData = when (val tile = DungeonMap.grid[row * GRID_SIZE + col]) {
            is MapTile.Room -> tile.data
            is MapTile.Connection -> tile.data
            else -> return errorMessage("Current tile is not a room (walk further into the room core and retry)")
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
        val pad = ROOM_SIZE
        val originX = START_X + minCol * HALF_ROOM - pad
        val originZ = START_Z + minRow * HALF_ROOM - pad
        val width = (maxCol - minCol) / 2 * ROOM_SIZE + ROOM_SIZE - 1 + 2 * pad
        val length = (maxRow - minRow) / 2 * ROOM_SIZE + ROOM_SIZE - 1 + 2 * pad

        val missingChunks = mutableListOf<String>()
        for (cx in (originX shr 4)..((originX + width) shr 4))
            for (cz in (originZ shr 4)..((originZ + length) shr 4))
                if (world.chunkSource.getChunk(cx, cz, false) == null) missingChunks.add("$cx,$cz")
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

        val entities = mutableListOf<String>()
        val maxX = (originX + width).toDouble(); val maxZ = (originZ + length).toDouble()
        val maxY = (bottom + height).toDouble()
        for (entity in world.entitiesForRendering()) {
            if (entity is Player) continue
            if (entity.x < originX || entity.x > maxX || entity.z < originZ || entity.z > maxZ) continue
            if (entity.y < bottom || entity.y > maxY) continue
            EntityCodec.encode(entity, originX, bottom, originZ, LOGGER)?.let { entities.add(it) }
        }

        val shape = roomData.shape
        val type = roomData.type.name.lowercase()
        val file = File(roomsDir, "${sanitize(roomData.name)}.json")
        file.writeText(buildJson(roomData.name, shape, type, originX, bottom, originZ, width, length, height, palette, runs, entities))

        modMessage("§aSaved §f${file.name} §a($shape $type) §7| §fcells=${cells.size} §fpalette=${palette.size} §fruns=${runs.size / 2} §fentities=${entities.size} §f${file.length() / 1024}KB")
    }

    private fun findYBounds(originX: Int, originZ: Int, width: Int, length: Int): Pair<Int, Int> {
        val world = mc.level ?: return 0 to 0
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

    private fun buildJson(name: String, shape: String, type: String, originX: Int, bottom: Int, originZ: Int, width: Int, length: Int, height: Int, palette: List<String>, runs: List<Int>, entities: List<String>): String {
        val sb = StringBuilder()
        sb.append("{\"n\":\"").append(name).append("\",\"s\":\"").append(shape).append("\",\"t\":\"").append(type)
            .append("\",\"ox\":").append(originX).append(",\"oz\":").append(originZ)
            .append(",\"b\":").append(bottom).append(",\"w\":").append(width).append(",\"l\":").append(length)
            .append(",\"h\":").append(height).append(",\"p\":[")
        palette.forEachIndexed { i, s -> if (i > 0) sb.append(','); sb.append('"').append(s).append('"') }
        sb.append("],\"d\":[")
        runs.forEachIndexed { i, v -> if (i > 0) sb.append(','); sb.append(v) }
        sb.append("],\"e\":[")
        entities.forEachIndexed { i, nbt ->
            if (i > 0) sb.append(',')
            sb.append('"').append(nbt.replace("\\", "\\\\").replace("\"", "\\\"")).append('"')
        }
        sb.append("]}")
        return sb.toString()
    }
}
