package gobby.utils.skyblock.dungeon

import gobby.features.dungeons.DungeonMap
import gobby.utils.copy.BlockStateCodec
import gobby.utils.copy.EntityCodec
import gobby.utils.skyblock.dungeon.map.MapConstants
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomData
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.client.multiplayer.ClientLevel
import org.slf4j.Logger

internal data class RoomCopyPlan(
    val name: String,
    val shape: String,
    val type: String,
    val originX: Int,
    val bottom: Int,
    val originZ: Int,
    val width: Int,
    val length: Int,
    val height: Int,
    val cells: Int
)

internal data class RoomBlockCapture(val palette: List<String>, val runs: List<Int>)

internal object RoomCopyCapture {
    private const val ROOM_SIZE = 32
    private const val SCAN_Y_MIN = 0
    private const val SCAN_Y_MAX = 160

    fun resolvePlan(world: ClientLevel, player: Player): Result<RoomCopyPlan> {
        val cell = resolveCell(player) ?: return Result.failure(IllegalArgumentException("Not on dungeon grid"))
        val roomData = resolveRoomData(cell) ?: return Result.failure(IllegalArgumentException("Current tile is not a room"))
        val cells = findCells(roomData)
        if (cells.isEmpty()) return Result.failure(IllegalArgumentException("Could not resolve room cells"))
        val plan = createPlan(roomData, cells)
        if (missingChunks(world, plan).isNotEmpty()) return Result.failure(IllegalArgumentException("Chunks not loaded"))
        val bounds = findYBounds(world, plan)
        if (bounds.second <= 0) return Result.failure(IllegalArgumentException("Empty column under room"))
        return Result.success(plan.copy(bottom = bounds.first, height = bounds.second))
    }

    fun captureBlocks(world: ClientLevel, plan: RoomCopyPlan): RoomBlockCapture {
        val palette = mutableListOf("minecraft:air")
        val indices = hashMapOf("minecraft:air" to 0)
        val runs = mutableListOf<Int>()
        var currentIndex = 0
        var currentLength = 0
        positions(plan).forEach { position ->
            val state = world.getBlockState(position)
            val key = if (state.isAir) "minecraft:air" else BlockStateCodec.encode(state)
            val index = indices.getOrPut(key) { palette.add(key)
            palette.lastIndex }
            if (index == currentIndex) currentLength++ else {
                appendRun(runs, currentIndex, currentLength)
                currentIndex = index
                currentLength = 1
            }
        }
        appendRun(runs, currentIndex, currentLength)
        return RoomBlockCapture(palette, runs)
    }

    fun captureEntities(world: ClientLevel, plan: RoomCopyPlan, logger: Logger): List<String> =
        world.entitiesForRendering().asSequence()
            .filterNot { it is Player }
            .filter { it.x in plan.originX.toDouble()..(plan.originX + plan.width).toDouble() }
            .filter { it.z in plan.originZ.toDouble()..(plan.originZ + plan.length).toDouble() }
            .filter { it.y in plan.bottom.toDouble()..(plan.bottom + plan.height).toDouble() }
            .mapNotNull { EntityCodec.encode(it, plan.originX, plan.bottom, plan.originZ, logger) }
            .toList()

    fun buildJson(plan: RoomCopyPlan, blocks: RoomBlockCapture, entities: List<String>): String {
        val json = StringBuilder()
        json.append("{\"n\":\"").append(plan.name).append("\",\"s\":\"").append(plan.shape).append("\",\"t\":\"").append(plan.type)
            .append("\",\"ox\":").append(plan.originX).append(",\"oz\":").append(plan.originZ)
            .append(",\"b\":").append(plan.bottom).append(",\"w\":").append(plan.width).append(",\"l\":").append(plan.length)
            .append(",\"h\":").append(plan.height).append(",\"p\":[")
        appendStrings(json, blocks.palette)
        json.append("],\"d\":[")
        blocks.runs.forEachIndexed { index, value -> if (index > 0) json.append(','); json.append(value) }
        json.append("],\"e\":[")
        appendStrings(json, entities.map { it.replace("\\", "\\\\").replace("\"", "\\\"") })
        return json.append("]}").toString()
    }

    private fun resolveCell(player: Player): Pair<Int, Int>? {
        val col = (player.blockPosition().x - MapConstants.START_X) / MapConstants.HALF_ROOM
        val row = (player.blockPosition().z - MapConstants.START_Z) / MapConstants.HALF_ROOM
        return if (col in 0 until MapConstants.GRID_SIZE && row in 0 until MapConstants.GRID_SIZE) col / 2 * 2 to row / 2 * 2 else null
    }

    private fun resolveRoomData(cell: Pair<Int, Int>): RoomData? = when (val tile = DungeonMap.grid[cell.second * MapConstants.GRID_SIZE + cell.first]) {
        is MapTile.Room -> tile.data
        is MapTile.Connection -> tile.data
        else -> null
    }

    private fun findCells(roomData: RoomData): List<Pair<Int, Int>> = DungeonMap.grid.indices.asSequence()
        .filter { it / MapConstants.GRID_SIZE % 2 == 0 && it % MapConstants.GRID_SIZE % 2 == 0 }
        .filter { (DungeonMap.grid[it] as? MapTile.Room)?.data === roomData }
        .map { it % MapConstants.GRID_SIZE to it / MapConstants.GRID_SIZE }
        .toList()

    private fun createPlan(roomData: RoomData, cells: List<Pair<Int, Int>>): RoomCopyPlan {
        val minCol = cells.minOf { it.first }
        val maxCol = cells.maxOf { it.first }
        val minRow = cells.minOf { it.second }
        val maxRow = cells.maxOf { it.second }
        val pad = ROOM_SIZE
        val width = (maxCol - minCol) / 2 * ROOM_SIZE + ROOM_SIZE - 1 + 2 * pad
        val length = (maxRow - minRow) / 2 * ROOM_SIZE + ROOM_SIZE - 1 + 2 * pad
        return RoomCopyPlan(roomData.name, roomData.shape, roomData.type.name.lowercase(), MapConstants.START_X + minCol * MapConstants.HALF_ROOM - pad, 0, MapConstants.START_Z + minRow * MapConstants.HALF_ROOM - pad, width, length, 0, cells.size)
    }

    private fun missingChunks(world: ClientLevel, plan: RoomCopyPlan): List<String> =
        ((plan.originX shr 4)..((plan.originX + plan.width) shr 4)).asSequence()
            .flatMap { x -> ((plan.originZ shr 4)..((plan.originZ + plan.length) shr 4)).asSequence().map { z -> x to z } }
            .filter { (x, z) -> world.chunkSource.getChunk(x, z, false) == null }
            .map { (x, z) -> "$x,$z" }
            .toList()

    private fun findYBounds(world: ClientLevel, plan: RoomCopyPlan): Pair<Int, Int> {
        val ys = (0 until plan.width step 2).asSequence()
            .flatMap { x -> (0 until plan.length step 2).asSequence().flatMap { z -> (SCAN_Y_MIN..SCAN_Y_MAX).asSequence().map { y -> BlockPos(plan.originX + x, y, plan.originZ + z) } } }
            .filter { !world.getBlockState(it).isAir }
            .map { it.y }
            .toList()
        val min = ys.minOrNull() ?: return 0 to 0
        return min to (ys.maxOrNull()!! - min + 1)
    }

    private fun positions(plan: RoomCopyPlan): Sequence<BlockPos> = (0 until plan.width * plan.length * plan.height).asSequence().map { index ->
        val x = index % plan.width
        val z = index / plan.width % plan.length
        val y = index / (plan.width * plan.length)
        BlockPos(plan.originX + x, plan.bottom + y, plan.originZ + z)
    }

    private fun appendRun(runs: MutableList<Int>, index: Int, length: Int) {
        if (length > 0) { runs.add(index); runs.add(length) }
    }

    private fun appendStrings(json: StringBuilder, values: List<String>) {
        values.forEachIndexed { index, value -> if (index > 0) json.append(','); json.append('"').append(value).append('"') }
    }
}
