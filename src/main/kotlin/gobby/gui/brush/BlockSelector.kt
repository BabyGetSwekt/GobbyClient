package gobby.gui.brush

import gg.essential.elementa.ElementaVersion
import gg.essential.elementa.WindowScreen
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.constraints.CramSiblingConstraint
import gg.essential.elementa.dsl.*
import gobby.features.dungeons.Brush
import gobby.gui.components.*
import gobby.gui.font.StyledFontProvider
import gobby.utils.ChatUtils.modMessage
import net.minecraft.world.level.block.Block
import net.minecraft.client.gui.GuiGraphicsExtractor as GuiGraphics
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.core.registries.BuiltInRegistries
import org.lwjgl.glfw.GLFW

private const val PANEL_WIDTH = 374f
private const val PANEL_HEIGHT = 280f
private const val STATUS_BAR_HEIGHT = 16f
private const val FAVOURITES_WIDTH = 52f
private const val FAVOURITES_HEIGHT = 14f
private const val SEARCH_Y = 28f
private const val SEARCH_HEIGHT = 14f
private const val LIST_TOP = 48f
private const val LIST_BOTTOM_RESERVED = 68f
private const val ITEM_SIZE = 20f
private const val ITEM_GAP = 2f

class BlockSelector private constructor(
    private val onSelect: ((Block) -> Unit)?
) : WindowScreen(
    version = ElementaVersion.V6,
    drawDefaultBackground = false
) {

    private val allEntries = mutableListOf<BlockSelectorEntry>()
    private var visibleEntries = listOf<BlockSelectorEntry>()
    private var lastQuery = ""
    private var showingFavorites = false

    private val panel = GobbyPanel(
        window,
        title = "Gobby Client's Block Selector",
        font = StyledFontProvider,
        bottomBarHeight = STATUS_BAR_HEIGHT,
        onDismiss = { displayScreen(null) }
    ).constrain {
        width = PANEL_WIDTH.pixels
        height = PANEL_HEIGHT.pixels
    }

    private val favouritesButton = GobbyToggleButton(
        activeText = "§c♥ §rFavs",
        inactiveText = "Favorites",
        font = StyledFontProvider,
        onToggle = { active -> applyFavouritesFilter(active) }
    ).constrain {
        x = ComponentTheme.SIDE_PAD.pixels(alignOpposite = true)
        y = CenterConstraint()
        width = FAVOURITES_WIDTH.pixels
        height = FAVOURITES_HEIGHT.pixels
    } childOf panel.titleBar

    private val searchField = GobbyTextField(
        placeholder = "Search...",
        font = StyledFontProvider,
        onChange = { query -> onQueryChanged(query) }
    ).constrain {
        x = ComponentTheme.SIDE_PAD.pixels
        y = SEARCH_Y.pixels
        width = 100.percent - (ComponentTheme.SIDE_PAD * 2).pixels
        height = SEARCH_HEIGHT.pixels
    } childOf panel

    private val scrollPanel = panel.contentArea(
        GobbyScrollPanel(emptyString = "No blocks found :("),
        LIST_TOP, LIST_BOTTOM_RESERVED
    )

    private val statusText = panel.label("", textColor = ComponentTheme.TEXT_MUTED).constrain {
        x = ComponentTheme.SIDE_PAD.pixels
        y = CenterConstraint()
    }

    init {
        panel.bottomBar?.let { statusText childOf it }
        showingFavorites = Brush.showFavoritesOnOpen
        favouritesButton.setActive(showingFavorites)
        populateBlocks()
    }

    private fun onQueryChanged(query: String) {
        if (query == lastQuery) return
        lastQuery = query
        refreshFilter()
    }

    private fun applyFavouritesFilter(active: Boolean) {
        showingFavorites = active
        Brush.showFavoritesOnOpen = active
        refreshFilter()
    }

    private fun populateBlocks() {
        BuiltInRegistries.BLOCK.filterNot { it.asItem() == Items.AIR }.forEach(::addBlock)
        allEntries.sortBy { it.id }
        refreshFilter()
    }

    private fun addBlock(block: Block) {
        val id = BuiltInRegistries.BLOCK.getKey(block).toString()
        val stack = ItemStack(block.asItem())
        val component = BlockItemComponent(id, stack).constrain {
            x = CramSiblingConstraint(ITEM_GAP)
            y = CramSiblingConstraint(ITEM_GAP)
            width = ITEM_SIZE.pixels
            height = ITEM_SIZE.pixels
        }
        component.onMouseEnter { statusText.setText(id) }
        component.onMouseLeave { statusText.setText("") }
        component.onMouseClick { event ->
            when (event.mouseButton) {
                GLFW.GLFW_MOUSE_BUTTON_LEFT -> selectBlock(block, id)
                GLFW.GLFW_MOUSE_BUTTON_RIGHT -> toggleFavourite(id)
            }
        }
        allEntries.add(BlockSelectorEntry(block, id, stack, component))
    }

    private fun selectBlock(block: Block, id: String) {
        selectedBlock = block
        modMessage("Selected block: §a$id")
        onSelect?.invoke(block)
        displayScreen(null)
    }

    private fun toggleFavourite(id: String) {
        val added = Brush.toggleFavorite(id)
        modMessage(if (added) "§e★ §aFavorited: §f$id" else "§7Unfavorited: §f$id")
        if (showingFavorites) refreshFilter()
    }

    private fun refreshFilter() {
        scrollPanel.scrollArea.clearChildren()
        val query = lastQuery.lowercase().trim()
        visibleEntries = allEntries
            .filter { query.isEmpty() || it.id.contains(query) }
            .filter { !showingFavorites || Brush.isFavorite(it.id) }
        visibleEntries.forEach { it.component childOf scrollPanel.scrollArea }
    }

    fun drawBlockItems(context: GuiGraphics) = BlockSelectorRenderer.draw(context, visibleEntries, scrollPanel)

    override fun onScreenClose() {
        super.onScreenClose()
        activeInstance = null
    }

    companion object {
        var selectedBlock: Block? = null

        @JvmStatic
        var currentDrawContext: GuiGraphics? = null

        private var activeInstance: BlockSelector? = null

        fun open(onSelect: ((Block) -> Unit)? = null) {
            val selector = BlockSelector(onSelect)
            activeInstance = selector
            displayScreen(selector)
        }

        @JvmStatic
        fun drawBlockItemsIfActive(context: GuiGraphics) {
            activeInstance?.drawBlockItems(context)
        }
    }
}
