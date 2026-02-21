package gobby.gui.brush

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIRoundedRectangle
import gg.essential.elementa.components.UIText
import gg.essential.elementa.components.input.UITextInput
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.CramSiblingConstraint
import gg.essential.elementa.dsl.*
import gg.essential.elementa.effects.OutlineEffect
import gg.essential.universal.UScreen
import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.Brush
import gobby.features.dungeons.EtherwarpTriggerbot
import gobby.gui.components.BlockItemComponent
import gobby.gui.components.GobbyScrollPanel
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
    private var showingFavorites = false

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

    private val favBtnBg by UIRoundedRectangle(3f).constrain {
        x = 8.pixels(alignOpposite = true)
        y = CenterConstraint()
        width = 52.pixels
        height = 14.pixels
        color = Color(40, 40, 50).toConstraint()
    } childOf titleBar

    private val favBtnText by UIText("Favorites", shadow = true).constrain {
        x = CenterConstraint()
        y = CenterConstraint()
        color = Color(180, 180, 190).toConstraint()
    } childOf favBtnBg

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

    private val scrollPanel by GobbyScrollPanel(
        emptyString = "No blocks found :(",
        innerPadding = 4f,
        pixelsPerScroll = 40f,
        scrollAcceleration = 1.8f
    ).constrain {
        x = 8.pixels
        y = 48.pixels
        width = 100.percent - 16.pixels
        height = 100.percent - 68.pixels
    } childOf panel

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

        favBtnBg.onMouseClick {
            showingFavorites = !showingFavorites
            if (showingFavorites) {
                favBtnBg.setColor(Color(180, 50, 50))
                favBtnText.setText("§c♥ §rFavs")
            } else {
                favBtnBg.setColor(Color(40, 40, 50))
                favBtnText.setText("Favorites")
            }
            refreshFilter()
        }

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
                } else if (event.mouseButton == 1) {
                    val added = Brush.toggleFavorite(id)
                    modMessage(if (added) "§e★ §aFavorited: §f$id" else "§7Unfavorited: §f$id")
                    if (showingFavorites) refreshFilter()
                }
            }

            allEntries.add(BlockEntry(block, id, stack, component))
        }

        allEntries.sortBy { it.id }
        showEntries(allEntries)
    }

    private fun filterBlocks(query: String) {
        scrollPanel.scrollArea.clearChildren()
        val lower = query.lowercase().trim()
        var filtered = if (lower.isEmpty()) allEntries else allEntries.filter { it.id.contains(lower) }
        if (showingFavorites) filtered = filtered.filter { Brush.isFavorite(it.id) }
        showEntries(filtered)
    }

    private fun refreshFilter() {
        filterBlocks(lastQuery)
    }

    private fun showEntries(entries: List<BlockEntry>) {
        visibleEntries = entries.toList()
        for (entry in visibleEntries) {
            entry.component childOf scrollPanel.scrollArea
        }
    }

    fun drawBlockItems(context: DrawContext) {
        val clipLeft = scrollPanel.scrollArea.getLeft().toInt()
        val clipTop = scrollPanel.scrollArea.getTop().toInt()
        val clipRight = scrollPanel.scrollArea.getRight().toInt()
        val clipBottom = scrollPanel.scrollArea.getBottom().toInt()

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

            if (entry.block in EtherwarpTriggerbot.TARGET_BLOCKS) {
                val c = BlockItemComponent.ETHERWARP_COLOR
                context.fill(left, top, right, top + 1, c)
                context.fill(left, bottom - 1, right, bottom, c)
                context.fill(left, top, left + 1, bottom, c)
                context.fill(right - 1, top, right, bottom, c)
            }

            if (Brush.isFavorite(entry.id)) {
                context.drawText(mc.textRenderer, "§c♥", right - 7, bottom - 8, 0xFFFFFFFF.toInt(), true)
            }
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
