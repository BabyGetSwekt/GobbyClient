package gobby.gui.brush

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.components.input.UITextInput
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.CramSiblingConstraint
import gg.essential.elementa.constraints.animation.Animations
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.universal.UScreen
import gobby.gui.components.BlockItemComponent
import gobby.utils.ChatUtils.modMessage
import net.minecraft.block.Block
import net.minecraft.client.gui.DrawContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import java.awt.Color

class BlockSelector private constructor (
    private val onSelect: ((Block) -> Unit)?
) : WindowScreen(
    version = ElementaVersion.V6,
    drawDefaultBackground = false
) {

    private val allEntries = mutableListOf<BlockEntry>()
    private var visibleEntries = listOf<BlockEntry>()
    private var lastQuery = ""

    private val overlay by UIBlock(Color(0, 0, 0, 100)).constrain {
        width = 100.percent
        height = 100.percent
    } childOf window

    private val panel by UIRoundedRectangle(5f).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        width = 374.pixels
        height = 280.pixels
        color = Color(18, 18, 22, 240).toConstraint()
    } childOf window

    private val titleBar by UIBlock(Color(26, 26, 32, 255)).constrain {
        width = 100.percent
        height = 22.pixels
    } childOf panel

    private val titleText by UIText("Gobby Client's Block Selector", shadow = true).constrain {
        x = 8.pixels
        y = CenterConstraint()
        color = Color(210, 210, 215).toConstraint()
    } childOf titleBar

    private val searchBg by UIRoundedRectangle(3f).constrain {
        x = 8.pixels
        y = 28.pixels
        width = 100.percent - 16.pixels
        height = 14.pixels
        color = Color(12, 12, 16, 220).toConstraint()
    } childOf panel

    private val searchInput by UITextInput(placeholder = "Search...").constrain {
        x = 4.pixels
        y = CenterConstraint()
        width = 100.percent - 8.pixels
        height = 9.pixels
        color = Color(200, 200, 200).toConstraint()
    } childOf searchBg

    private val scrollArea by ScrollComponent(
        emptyString = "No blocks found :(",
        innerPadding = 4f,
        pixelsPerScroll = 40f,
        scrollAcceleration = 1.8f
    ).constrain {
        x = 8.pixels
        y = 48.pixels
        width = 100.percent - 30.pixels
        height = 100.percent - 68.pixels
    } childOf panel

    private val scrollTrack by UIRoundedRectangle(3f).constrain {
        x = 5.pixels(alignOpposite = true)
        y = 48.pixels
        width = 8.pixels
        height = 100.percent - 68.pixels
        color = Color(12, 12, 16, 180).toConstraint()
    } childOf panel

    private val scrollGrip by UIRoundedRectangle(3f).constrain {
        x = CenterConstraint()
        width = 6.pixels
        color = Color(70, 70, 80, 200).toConstraint()
    } childOf scrollTrack

    private val statusBar by UIBlock(Color(26, 26, 32, 255)).constrain {
        y = 0.pixels(alignOpposite = true)
        width = 100.percent
        height = 16.pixels
    } childOf panel

    private val statusText by UIText("", shadow = true).constrain {
        x = 8.pixels
        y = CenterConstraint()
        color = Color(140, 140, 150).toConstraint()
    } childOf statusBar

    init {
        scrollArea.setVerticalScrollBarComponent(scrollGrip, hideWhenUseless = true)

        scrollGrip.onMouseEnter {
            scrollGrip.animate {
                setColorAnimation(Animations.OUT_EXP, 0.15f, Color(110, 110, 130, 240).toConstraint())
            }
        }
        scrollGrip.onMouseLeave {
            scrollGrip.animate {
                setColorAnimation(Animations.OUT_EXP, 0.2f, Color(70, 70, 80, 200).toConstraint())
            }
        }

        searchInput.onKeyType { _, _ ->
            val query = searchInput.getText()
            if (query != lastQuery) {
                lastQuery = query
                filterBlocks(query)
            }
        }
        searchBg.onMouseClick { searchInput.grabWindowFocus() }
        searchBg.enableEffect(OutlineEffect(Color(40, 40, 50), 1f))
        overlay.onMouseClick { UScreen.displayScreen(null) }

        populateBlocks()
    }

    private fun populateBlocks() {
        for (block in Registries.BLOCK) {
            if (block.asItem() == Items.AIR) continue

            val id = Registries.BLOCK.getId(block).toString()
            val stack = ItemStack(block.asItem())
            val component = BlockItemComponent(id, stack).constrain {
                x = CramSiblingConstraint(2f)
                y = CramSiblingConstraint(2f)
                width = 20.pixels
                height = 20.pixels
            }

            component.onMouseEnter { statusText.setText(id) }
            component.onMouseLeave { statusText.setText("") }
            component.onMouseClick { event ->
                if (event.mouseButton == 0) {
                    selectedBlock = block
                    modMessage("Selected block: §a$id")
                    onSelect?.invoke(block)
                    displayScreen(null)
                }
            }

            allEntries.add(BlockEntry(block, id, stack, component))
        }

        allEntries.sortBy { it.id }
        showEntries(allEntries)
    }

    private fun filterBlocks(query: String) {
        scrollArea.clearChildren()
        val lower = query.lowercase().trim()
        val filtered = if (lower.isEmpty()) allEntries else allEntries.filter { it.id.contains(lower) }
        showEntries(filtered)
    }

    private fun showEntries(entries: List<BlockEntry>) {
        visibleEntries = entries.toList()
        for (entry in visibleEntries) {
            entry.component childOf scrollArea
        }
    }

    fun drawBlockItems(context: DrawContext) {
        val clipLeft = scrollArea.getLeft().toInt()
        val clipTop = scrollArea.getTop().toInt()
        val clipRight = scrollArea.getRight().toInt()
        val clipBottom = scrollArea.getBottom().toInt()

        context.enableScissor(clipLeft, clipTop, clipRight, clipBottom)
        for (entry in visibleEntries) {
            val comp = entry.component
            val left = comp.getLeft().toInt()
            val top = comp.getTop().toInt()
            val right = comp.getRight().toInt()
            val bottom = comp.getBottom().toInt()
            if (top + 20 < clipTop || top > clipBottom) continue
            val bg = when {
                entry.block == selectedBlock -> BlockItemComponent.SELECTED_COLOR
                comp.mouseOver -> BlockItemComponent.HOVER_COLOR
                else -> BlockItemComponent.BG_COLOR
            }
            context.fill(left, top, right, bottom, bg)
            context.drawItem(entry.stack, left + 2, top + 2)
        }
        context.disableScissor()
    }

    override fun onScreenClose() {
        super.onScreenClose()
        activeInstance = null
    }

    private data class BlockEntry(
        val block: Block,
        val id: String,
        val stack: ItemStack,
        val component: BlockItemComponent
    )

    companion object {
        var selectedBlock: Block? = null

        @JvmStatic
        var currentDrawContext: DrawContext? = null

        private var activeInstance: BlockSelector? = null

        fun open(onSelect: ((Block) -> Unit)? = null) {
            val selector = BlockSelector(onSelect)
            activeInstance = selector
            displayScreen(selector)
        }

        @JvmStatic
        fun drawBlockItemsIfActive(context: DrawContext) {
            activeInstance?.drawBlockItems(context)
        }
    }
}
