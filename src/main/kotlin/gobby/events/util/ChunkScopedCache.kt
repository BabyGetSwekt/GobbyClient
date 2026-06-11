package gobby.events.util

import gobby.events.BlockStateChangeEvent
import gobby.events.ChunkLoadEvent
import gobby.events.ChunkUnloadEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

abstract class ChunkScopedCache {

    @SubscribeEvent
    fun handleChunkUnload(event: ChunkUnloadEvent) {
        val pos = event.chunk.pos
        onChunkEvicted(pos.x, pos.z)
    }

    @SubscribeEvent
    fun handleChunkLoad(event: ChunkLoadEvent) {
        val pos = event.chunk.pos
        onChunkLoaded(pos.x, pos.z)
    }

    @SubscribeEvent
    fun handleBlockStateChange(event: BlockStateChangeEvent) {
        onPosEvicted(event.blockPos, event.newState)
    }

    @SubscribeEvent
    fun handleWorldLoad(event: WorldLoadEvent) {
        onAllEvicted()
    }

    protected open fun onChunkEvicted(chunkX: Int, chunkZ: Int) {}
    protected open fun onChunkLoaded(chunkX: Int, chunkZ: Int) {}
    protected abstract fun onPosEvicted(pos: BlockPos, newState: BlockState)
    protected abstract fun onAllEvicted()
}
