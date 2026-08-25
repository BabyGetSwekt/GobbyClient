package gobby.pathfinder.world

import gobby.utils.skyblock.EtherwarpCoordinateSource
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

internal class SnapshotCursor private constructor() : EtherwarpCoordinateSource {
    private lateinit var snapshot: BlockCache.SnapshotView
    private var chunkX = Int.MIN_VALUE
    private var chunkZ = Int.MIN_VALUE
    private var chunk: BlockCache.ChunkSnapshot? = null

    fun reset(view: BlockCache.SnapshotView): SnapshotCursor {
        snapshot = view
        chunkX = Int.MIN_VALUE
        chunkZ = Int.MIN_VALUE
        chunk = null
        return this
    }

    override fun stateAt(x: Int, y: Int, z: Int): BlockState? {
        if (snapshot.isDirectSnapshot) return snapshot.stateAt(x, y, z)
        if (snapshot.isPassableOverride(BlockPos.asLong(x, y, z))) return AIR
        select(x shr 4, z shr 4)
        return chunk?.blockState(x, y, z)
    }

    private fun select(nextChunkX: Int, nextChunkZ: Int) {
        if (nextChunkX == chunkX && nextChunkZ == chunkZ) return
        chunkX = nextChunkX
        chunkZ = nextChunkZ
        chunk = snapshot.chunkSnapshot(nextChunkX, nextChunkZ)
    }

    companion object {
        val AIR = Blocks.AIR.defaultBlockState()
        val LOCAL = ThreadLocal.withInitial(::SnapshotCursor)

        fun threadLocal(snapshot: BlockCache.SnapshotView): SnapshotCursor = LOCAL.get().reset(snapshot)
    }
}
