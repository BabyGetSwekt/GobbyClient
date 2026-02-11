package gobby.utils.render

import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import net.minecraft.client.render.Camera
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.awt.Color

object RenderBlock {

    private data class BlockData(
        val pos: BlockPos,
        val color: Color,
        val filled: Boolean = false
    )

    private val blocks = mutableListOf<BlockData>()

    fun addBlock(pos: BlockPos, color: Color, filled: Boolean = false) {
        blocks.add(BlockData(pos, color, filled))
    }

    fun removeBlock(pos: BlockPos) {
        blocks.removeIf { it.pos == pos }
    }

    fun reset() {
        blocks.clear()
    }

    @SubscribeEvent
    fun onRender(event: NewRender3DEvent) {
        for (block in blocks) {
            renderBlock(event.matrixStack, event.camera, block)
        }
    }

    private fun renderBlock(matrixStack: MatrixStack, camera: Camera, block: BlockData) {
        val blockPos = block.pos

        val box = Box(
            blockPos.x.toDouble(), blockPos.y.toDouble(), blockPos.z.toDouble(),
            blockPos.x + 1.0, blockPos.y + 1.0, blockPos.z + 1.0
        )

        if (block.filled) {
            //BlockRenderUtils.drawFilledBox(matrixStack, camera, box, block.color)
            BlockRenderUtils.draw3DBox(matrixStack, camera, box, block.color)
        } else {
            BlockRenderUtils.draw3DBox(matrixStack, camera, box, block.color)
        }
    }
}
