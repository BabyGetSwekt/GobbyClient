package gobby.pathfinder.etherwarp

import gobby.utils.skyblock.EtherwarpWorldAccess
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

class SyntheticDungeonCache {
    companion object {
        const val ROOM_COLUMNS = 8
        const val ROOM_ROWS = 5
        const val ROOM_SPACING = 48
        const val ROOM_RADIUS = 3
        const val CORRIDOR_LENGTH = 384
        const val CORRIDOR_STEP = 1
        const val MIN_Y = 0
        const val MAX_Y = 320
    }

    private val air = Blocks.AIR.defaultBlockState()
    private val floor = Blocks.STONE.defaultBlockState()
    private val blockPositions = buildPositions()
    private val blocks = blockPositions.associate { it.asLong() to floor }

    val candidates: List<BlockPos> = blockPositions.sortedWith(compareBy({ it.x }, { it.z }))
    val goal: BlockPos = roomOrigin(ROOM_COLUMNS - 1, ROOM_ROWS - 1)
    val access = EtherwarpWorldAccess(MIN_Y, MAX_Y, ::stateAt)

    private fun stateAt(pos: BlockPos): BlockState = blocks[pos.asLong()] ?: air

    private fun buildPositions(): List<BlockPos> {
        val corridor = (0..CORRIDOR_LENGTH step CORRIDOR_STEP).map { BlockPos(it, 0, 0) }
        val rooms = (0 until ROOM_COLUMNS).asSequence()
            .flatMap { column -> (0 until ROOM_ROWS).asSequence().flatMap { row -> roomBlocks(column, row) } }
            .toList()
        return corridor + rooms
    }

    private fun roomBlocks(column: Int, row: Int): Sequence<BlockPos> {
        val origin = roomOrigin(column, row)
        return (-ROOM_RADIUS..ROOM_RADIUS).asSequence().flatMap { dx ->
            (-ROOM_RADIUS..ROOM_RADIUS).asSequence().map { dz -> BlockPos(origin.x + dx, 0, origin.z + dz) }
        }
    }

    private fun roomOrigin(column: Int, row: Int): BlockPos =
        BlockPos(column * ROOM_SPACING + ROOM_SPACING / 3, 0, row * ROOM_SPACING + ROOM_SPACING / 3)
}
