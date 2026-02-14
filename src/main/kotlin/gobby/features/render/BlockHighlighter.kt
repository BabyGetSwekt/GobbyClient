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
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.chunk.WorldChunk
import java.awt.Color

abstract class BlockHighlighter {

    protected val highlightedBlocks = ObjectOpenHashSet<BlockPos>()

    @SubscribeEvent
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!isEnabled()) return
        scanChunk(event.chunk)
    }

    protected fun scanChunk(chunk: WorldChunk) {
        val predicate = getStatePredicate()

        val sections = chunk.sectionArray
        for (i in sections.indices) {
            val section = sections[i] ?: continue
            if (section.isEmpty) continue

            val baseY = chunk.bottomY + (i * 16)
            for (x in 0..15) {
                for (y in 0..15) {
                    for (z in 0..15) {
                        val state = section.getBlockState(x, y, z)
                        if (predicate(state)) {
                            val pos = BlockPos(
                                chunk.pos.startX + x,
                                baseY + y,
                                chunk.pos.startZ + z
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
        val world = mc.world ?: return
        val viewDistance = mc.options.viewDistance.value
        val player = mc.player ?: return
        val playerChunkX = player.blockPos.x shr 4
        val playerChunkZ = player.blockPos.z shr 4

        for (cx in (playerChunkX - viewDistance)..(playerChunkX + viewDistance)) {
            for (cz in (playerChunkZ - viewDistance)..(playerChunkZ + viewDistance)) {
                val chunk = world.chunkManager.getWorldChunk(cx, cz) ?: continue
                scanChunk(chunk)
            }
        }
    }

    @SubscribeEvent
    fun onChunkUnload(event: ChunkUnloadEvent) {
        if (!isEnabled()) return

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
            highlightedBlocks.add(event.blockPos.toImmutable())
        } else {
            highlightedBlocks.remove(event.blockPos)
        }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        if (!isEnabled()) return
        val world = mc.world ?: return

        val matrixStack = event.matrixStack
        val camera = event.camera
        for (pos in highlightedBlocks) {
            val color = getColor(pos)
            val blockState = world.getBlockState(pos)
            val outline = blockState.getOutlineShape(world, pos)
            val box = if (outline.isEmpty) {
                Box(pos)
            } else {
                outline.boundingBox.offset(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
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
}
