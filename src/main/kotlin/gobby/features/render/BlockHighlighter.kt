package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.BlockStateChangeEvent
import gobby.events.ChunkLoadEvent
import gobby.events.ChunkUnloadEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.render.BlockRenderUtils.draw3DBox
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.chunk.LevelChunk
import java.awt.Color

abstract class BlockHighlighter {

    enum class RenderMode { FULL_BLOCK, OUTLINE, NODE }


    protected val highlightedBlocks = ObjectOpenHashSet<BlockPos>()

    @SubscribeEvent
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!isEnabled()) return
        scanChunk(event.chunk)
    }

    protected fun scanChunk(chunk: LevelChunk) {
        val predicate = getStatePredicate()

        val sections = chunk.sections
        for (i in sections.indices) {
            val section = sections[i]

            val baseY = chunk.minY + (i * 16)
            for (x in 0..15) {
                for (y in 0..15) {
                    for (z in 0..15) {
                        val state = section.getBlockState(x, y, z)
                        if (predicate(state)) {
                            val pos = BlockPos(
                                chunk.pos.minBlockX + x,
                                baseY + y,
                                chunk.pos.minBlockZ + z
                            )
                            if (!isValidPosition(pos)) continue
                            highlightedBlocks.add(pos)
                        }
                    }
                }
            }
        }
    }

    protected fun scanLoadedChunks() {
        val world = mc.level ?: return
        val viewDistance = mc.options.renderDistance().get()
        val player = mc.player ?: return
        val playerChunkX = player.blockPosition().x shr 4
        val playerChunkZ = player.blockPosition().z shr 4

        for (cx in (playerChunkX - viewDistance)..(playerChunkX + viewDistance)) {
            for (cz in (playerChunkZ - viewDistance)..(playerChunkZ + viewDistance)) {
                val chunk = world.chunkSource.getChunk(cx, cz, false) ?: continue
                scanChunk(chunk)
            }
        }
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunkPos = event.chunk.pos
        highlightedBlocks.removeIf { pos ->
            pos.x shr 4 == chunkPos.x && pos.z shr 4 == chunkPos.z
        }
    }

    @SubscribeEvent
    fun onBlockStateChange(event: BlockStateChangeEvent) {
        if (!isEnabled()) return

        val predicate = getStatePredicate()
        if (predicate(event.newState) && isValidPosition(event.blockPos)) {
            highlightedBlocks.add(event.blockPos.immutable())
        } else {
            highlightedBlocks.remove(event.blockPos)
        }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!isEnabled()) return
        val world = mc.level ?: return

        val matrixStack = event.matrixStack
        val camera = event.camera
        for (pos in highlightedBlocks) {
            val color = getColor(pos)
            val box = when (renderMode()) {
                RenderMode.FULL_BLOCK -> AABB(pos)
                RenderMode.OUTLINE -> {
                    val blockState = world.getBlockState(pos)
                    val outline = blockState.getShape(world, pos)
                    if (outline.isEmpty) AABB(pos)
                    else outline.bounds().move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
                }
                RenderMode.NODE -> AABB(
                    pos.x + 0.25, pos.y.toDouble(), pos.z + 0.25,
                    pos.x + 0.75, pos.y + 0.5, pos.z + 0.75
                )
            }
            draw3DBox(matrixStack, camera, box, color, depthTest = depthTest())
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        highlightedBlocks.clear()
    }

    abstract fun isEnabled(): Boolean
    abstract fun getStatePredicate(): (BlockState) -> Boolean
    abstract fun getColor(pos: BlockPos): Color
    open fun isValidPosition(pos: BlockPos): Boolean = true
    open fun depthTest(): Boolean = false
    open fun renderMode(): RenderMode = RenderMode.FULL_BLOCK
}
