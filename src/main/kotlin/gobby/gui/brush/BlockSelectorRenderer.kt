package gobby.gui.brush

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.Brush
import gobby.gui.components.BlockItemComponent
import gobby.gui.components.GobbyScrollPanel
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block

internal data class BlockSelectorEntry(val block: Block, val id: String, val stack: ItemStack, val component: BlockItemComponent)

internal object BlockSelectorRenderer {
    fun draw(context: GuiGraphicsExtractor, entries: List<BlockSelectorEntry>, scrollPanel: GobbyScrollPanel) {
        val left = scrollPanel.scrollArea.getLeft().toInt()
        val top = scrollPanel.scrollArea.getTop().toInt()
        val right = scrollPanel.scrollArea.getRight().toInt()
        val bottom = scrollPanel.scrollArea.getBottom().toInt()
        context.enableScissor(left, top, right, bottom)
        entries.forEach { drawEntry(context, it, left, top, right, bottom) }
        context.disableScissor()
    }

    private fun drawEntry(context: GuiGraphicsExtractor, entry: BlockSelectorEntry, clipLeft: Int, clipTop: Int, clipRight: Int, clipBottom: Int) {
        val component = entry.component
        val left = component.getLeft().toInt()
        val top = component.getTop().toInt()
        val right = component.getRight().toInt()
        val bottom = component.getBottom().toInt()
        if (top + 20 < clipTop || top > clipBottom) return
        val background = when {
            entry.block == BlockSelector.selectedBlock -> BlockItemComponent.SELECTED_COLOR
            component.mouseOver -> BlockItemComponent.HOVER_COLOR
            else -> BlockItemComponent.BG_COLOR
        }
        context.fill(left, top, right, bottom, background)
        context.item(entry.stack, left + 2, top + 2)
        if (entry.block in EtherwarpUtils.TARGET_BLOCKS) drawBorder(context, left, top, right, bottom)
        if (Brush.isFavorite(entry.id)) context.text(mc.font, "§c♥", right - 7, bottom - 8, 0xFFFFFFFF.toInt(), true)
    }

    private fun drawBorder(context: GuiGraphicsExtractor, left: Int, top: Int, right: Int, bottom: Int) {
        val color = BlockItemComponent.ETHERWARP_COLOR
        context.fill(left, top, right, top + 1, color)
        context.fill(left, bottom - 1, right, bottom, color)
        context.fill(left, top, left + 1, bottom, color)
        context.fill(right - 1, top, right, bottom, color)
    }
}
