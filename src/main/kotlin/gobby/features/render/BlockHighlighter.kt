package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChunkLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.camera
import gobby.events.render.matrixStack
import gobby.events.util.ChunkScopedCache
import gobby.utils.render.BlockRenderUtils.draw3DBox
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.chunk.LevelChunk
import java.awt.Color

abstract class BlockHighlighter : ChunkScopedCache() {

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

    override fun onChunkEvicted(chunkX: Int, chunkZ: Int) {
        highlightedBlocks.removeIf { pos ->
            pos.x shr 4 == chunkX && pos.z shr 4 == chunkZ
        }
    }

    override fun onPosEvicted(pos: BlockPos, newState: BlockState) {
        if (!isEnabled()) return
        val predicate = getStatePredicate()
        if (predicate(newState) && isValidPosition(pos)) {
            highlightedBlocks.add(pos.immutable())
        } else {
            highlightedBlocks.remove(pos)
        }
    }

    override fun onAllEvicted() {
        highlightedBlocks.clear()
    }

    @SubscribeEvent
    fun onRender3D(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity) return
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

    abstract fun isEnabled(): Boolean

    abstract fun getStatePredicate(): (BlockState) -> Boolean

    abstract fun getColor(pos: BlockPos): Color

    open fun isValidPosition(pos: BlockPos): Boolean = true

    open fun depthTest(): Boolean = false

    open fun renderMode(): RenderMode = RenderMode.FULL_BLOCK
}
