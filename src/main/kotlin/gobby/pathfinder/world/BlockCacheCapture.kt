package gobby.pathfinder.world

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap

internal object BlockCacheCapture {
    private const val LOCAL_MASK = 15
    private const val LOCAL_SHIFT = 4
    private const val SECTION_SIZE = 16
    private const val CHUNK_AREA = SECTION_SIZE * SECTION_SIZE
    private const val BLOCKS_PER_SECTION = CHUNK_AREA * SECTION_SIZE

    fun capture(chunk: LevelChunk): BlockCache.ChunkSnapshot {
        val copied = arrayOfNulls<PalettedContainer<BlockState>>(chunk.sections.size)
        chunk.sections.forEachIndexed { index, section ->
            if (!section.hasOnlyAir()) copied[index] = section.states.copy()
        }
        val surface = IntArray(CHUNK_AREA) { index ->
            chunk.getHeight(Heightmap.Types.WORLD_SURFACE, index and LOCAL_MASK, index shr LOCAL_SHIFT)
        }
        val origin = chunk.pos
        val blocks = chunk.sections.indices.asSequence()
            .flatMap { section -> nonAirBlocks(origin.minBlockX, chunk.minY, origin.minBlockZ, copied[section], section) }
            .toList()
        return BlockCache.ChunkSnapshot(chunk.minY, copied, surface, blocks)
    }

    private fun nonAirBlocks(originX: Int, minY: Int, originZ: Int, section: PalettedContainer<BlockState>?, sectionIndex: Int): Sequence<BlockPos> =
        section?.let { container ->
            (0 until BLOCKS_PER_SECTION).asSequence()
                .map(::localPosition)
                .filter { !container.get(it.x, it.y, it.z).isAir }
                .map { local -> BlockPos(originX + local.x, minY + sectionIndex * SECTION_SIZE + local.y, originZ + local.z) }
        } ?: emptySequence()

    private fun localPosition(index: Int): BlockPos = BlockPos(
        index and LOCAL_MASK,
        (index shr 8) and LOCAL_MASK,
        (index shr LOCAL_SHIFT) and LOCAL_MASK
    )
}
