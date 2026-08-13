package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCacheDump
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RealDungeonCache private constructor(dump: BlockCacheDump.Dump) {

    companion object {
        private const val DUMP_PROPERTY = "gobby.dump"
        private const val DEFAULT_DUMP =
            "C:/Users/Kevin/AppData/Roaming/PrismLauncher/instances/26.2/minecraft/logs/gobby-blockcache.dump"
        private const val START_X = -185
        private const val START_Z = -185
        private const val HALF_ROOM = 16
        private const val GRID_SIZE = 11
        private const val FLOOR_MIN_Y = 60
        private const val FLOOR_MAX_Y = 82
        private const val ROOM_FLOOR_MIN_Y = 66
        private const val ROOM_FLOOR_MAX_Y = 73

        fun loadOrNull(): RealDungeonCache? {
            val path = Path.of(System.getProperty(DUMP_PROPERTY) ?: DEFAULT_DUMP)
            return BlockCacheDump.read(path)?.let { RealDungeonCache(it) }
        }

        fun roomCenter(col: Int, row: Int): BlockPos =
            BlockPos(START_X + col * HALF_ROOM, FLOOR_MIN_Y, START_Z + row * HALF_ROOM)
    }

    private val air: BlockState = Blocks.AIR.defaultBlockState()
    private val blocks = dump.blocks

    val totalBlocks: Int = blocks.size
    val access = EtherwarpWorldAccess(dump.minY, dump.maxY, blockSource = { blocks[it.asLong()] ?: air })

    val floorCandidates: List<BlockPos> = dump.positions()
        .filter { it.y in FLOOR_MIN_Y..FLOOR_MAX_Y && it.x >= START_X && it.z >= START_Z }
        .filter { EtherwarpUtils.isEtherwarpable(it, access) }

    fun landingNearRoom(col: Int, row: Int): BlockPos? {
        val center = roomCenter(col, row)
        return floorCandidates
            .filter { it.y in ROOM_FLOOR_MIN_Y..ROOM_FLOOR_MAX_Y }
            .filter { abs(it.x - center.x) <= HALF_ROOM && abs(it.z - center.z) <= HALF_ROOM }
            .minByOrNull { horizontalDistanceSq(it, center) }
    }

    fun occupiedRooms(): List<Pair<Int, Int>> =
        (0 until GRID_SIZE step 2).flatMap { col ->
            (0 until GRID_SIZE step 2).mapNotNull { row -> (col to row).takeIf { landingNearRoom(col, row) != null } }
        }

    fun corridorCandidates(from: BlockPos, to: BlockPos, margin: Int): List<BlockPos> {
        val minX = min(from.x, to.x) - margin
        val maxX = max(from.x, to.x) + margin
        val minZ = min(from.z, to.z) - margin
        val maxZ = max(from.z, to.z) + margin
        return floorCandidates.filter { it.x in minX..maxX && it.z in minZ..maxZ }
    }

    private fun horizontalDistanceSq(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return dx * dx + dz * dz
    }
}
