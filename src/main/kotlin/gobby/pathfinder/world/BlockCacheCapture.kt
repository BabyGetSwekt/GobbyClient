package gobby.pathfinder.world

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
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

    private data class CapturedSection(val fingerprint: Long, val nonAirBlocks: List<BlockPos>)

    fun capture(chunk: LevelChunk, previous: BlockCache.ChunkSnapshot? = null): BlockCache.ChunkSnapshot {
        val copied = arrayOfNulls<PalettedContainer<BlockState>>(chunk.sections.size)
        chunk.sections.forEachIndexed { index, section ->
            if (!section.hasOnlyAir()) copied[index] = section.states.copy()
        }
        val surface = IntArray(CHUNK_AREA) { index ->
            chunk.getHeight(Heightmap.Types.WORLD_SURFACE, index and LOCAL_MASK, index shr LOCAL_SHIFT)
        }
        val origin = chunk.pos
        val capturedSections = copied.mapIndexed { index, section ->
            captureSection(origin.minBlockX, chunk.minY, origin.minBlockZ, section, index)
        }
        val blocks = capturedSections.flatMap(CapturedSection::nonAirBlocks)
        val fingerprint = collisionFingerprint(capturedSections, surface)
        val revision = if (previous?.collisionFingerprint == fingerprint) previous.revision else nextRevision()
        return BlockCache.ChunkSnapshot(chunk.minY, copied, surface, blocks, revision, fingerprint)
    }

    private var revisionCounter = 0L

    private fun nextRevision(): Long = ++revisionCounter

    private fun captureSection(
        originX: Int,
        minY: Int,
        originZ: Int,
        section: PalettedContainer<BlockState>?,
        sectionIndex: Int
    ): CapturedSection {
        if (section == null) {
            val fingerprint = (FNV_OFFSET_BASIS xor sectionIndex.toLong()).let { (it xor NO_SECTION) * FNV_PRIME }
            return CapturedSection(fingerprint, emptyList())
        }
        val blocks = ArrayList<BlockPos>()
        var fingerprint = (FNV_OFFSET_BASIS xor sectionIndex.toLong()) * FNV_PRIME
        (0 until BLOCKS_PER_SECTION).forEach { index ->
            val local = localPosition(index)
            val state = section.get(local.x, local.y, local.z)
            fingerprint = (fingerprint xor Block.getId(state).toLong()) * FNV_PRIME
            if (!state.isAir) blocks += BlockPos(originX + local.x, minY + sectionIndex * SECTION_SIZE + local.y, originZ + local.z)
        }
        return CapturedSection(fingerprint, blocks)
    }

    private fun collisionFingerprint(
        sections: List<CapturedSection>,
        surface: IntArray
    ): Long = sections.asSequence()
        .map(CapturedSection::fingerprint)
        .plus(surface.asSequence().map(Int::toLong))
        .fold(FNV_OFFSET_BASIS) { hash, value -> (hash xor value) * FNV_PRIME }

    private const val NO_SECTION = -1L

    private fun localPosition(index: Int): BlockPos = BlockPos(
        index and LOCAL_MASK,
        (index shr 8) and LOCAL_MASK,
        (index shr LOCAL_SHIFT) and LOCAL_MASK
    )

    private const val FNV_OFFSET_BASIS = -3750763034362895579L
    private const val FNV_PRIME = 1099511628211L
}
