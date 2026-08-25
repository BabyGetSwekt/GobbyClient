package gobby.pathfinder.world

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

internal class SnapshotDirectIndex(private val states: Map<Long, BlockState>?) {
    private val index by lazy(LazyThreadSafetyMode.NONE, ::buildIndex)

    fun nonAirCandidates(key: Long): List<BlockPos> = index.nonAirByChunk[key].orEmpty()

    fun surfaceHeight(x: Int, z: Int): Int? = index.surfaceHeights[columnKey(x, z)]

    private fun buildIndex(): Index {
        val nonAirByChunk = HashMap<Long, MutableList<BlockPos>>()
        val surfaceHeights = HashMap<Long, Int>()
        val chunkFingerprints = HashMap<Long, Long>()
        states.orEmpty().forEach { (packed, state) ->
            if (state.isAir) return@forEach
            val position = BlockPos.of(packed)
            val chunk = chunkKey(position)
            nonAirByChunk.getOrPut(chunk, ::ArrayList).add(position)
            chunkFingerprints[chunk] = (chunkFingerprints[chunk] ?: EMPTY_FINGERPRINT) + entryFingerprint(packed, state)
            val column = columnKey(position)
            if (position.y > (surfaceHeights[column] ?: EMPTY_SURFACE)) surfaceHeights[column] = position.y
        }
        return Index(nonAirByChunk, surfaceHeights, chunkFingerprints)
    }

    fun chunkFingerprint(key: Long): Long? = index.chunkFingerprints[key]

    private fun chunkKey(pos: BlockPos): Long = chunkKey(pos.x shr 4, pos.z shr 4)

    private fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and CHUNK_MASK)

    private fun columnKey(pos: BlockPos): Long = columnKey(pos.x, pos.z)

    private fun columnKey(x: Int, z: Int): Long = (x.toLong() shl 32) xor (z.toLong() and CHUNK_MASK)

    private data class Index(
        val nonAirByChunk: Map<Long, List<BlockPos>>,
        val surfaceHeights: Map<Long, Int>,
        val chunkFingerprints: Map<Long, Long>
    )

    private fun entryFingerprint(packed: Long, state: BlockState): Long = mix(packed xor state.hashCode().toLong())

    private fun mix(value: Long): Long {
        var result = value
        result = (result xor (result ushr MIX_SHIFT_A)) * MIX_A
        result = (result xor (result ushr MIX_SHIFT_B)) * MIX_B
        return result xor (result ushr MIX_SHIFT_C)
    }

    private companion object {
        const val CHUNK_MASK = 0xFFFFFFFFL
        const val EMPTY_SURFACE = Int.MIN_VALUE
        const val EMPTY_FINGERPRINT = 0L
        const val MIX_SHIFT_A = 30
        const val MIX_SHIFT_B = 27
        const val MIX_SHIFT_C = 31
        const val MIX_A = -4658899252018542737L
        const val MIX_B = -7723592293110705685L
    }
}
