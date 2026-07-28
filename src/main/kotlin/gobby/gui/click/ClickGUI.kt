package gobby.gui.click

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.*
import net.minecraft.network.chat.Component

class ClickGUI : Screen(Component.literal("GobbyClient")) {

    companion object {
        var lastCategory: Category? = null
        var lastSettingsModule: Module? = null
        var sidebarExpanded: Boolean = false
    }

    internal var guiScale = 1f
    internal var drawOffsetX = 0
    internal var drawOffsetY = 0
    internal val panelX = 0
    internal val panelY = 0
    internal var sidebarExpand = 0f
    internal val sidebarWidth: Int get() = (SIDEBAR_W_COLLAPSED + (SIDEBAR_W_EXPANDED - SIDEBAR_W_COLLAPSED) * sidebarExpand).toInt()
    internal val contentX: Int get() = panelX + SIDEBAR_W_COLLAPSED + CONTENT_PAD
    internal val contentY: Int get() = panelY + HEADER_H + CONTENT_PAD
    internal val contentW: Int get() = PANEL_W - SIDEBAR_W_COLLAPSED - CONTENT_PAD * 2
    internal val contentH: Int get() = PANEL_H - HEADER_H - CONTENT_PAD * 2

    internal var currentCategory: Category = lastCategory ?: Category.entries.first()
    internal var settingsModule: Module? = lastSettingsModule

    internal var searchQuery = ""
    internal var searchFocused = false
    internal var searchSelectAll = false
    internal var suppressNextChar = false

    internal var scrollOffset = 0f
    internal var scrollTarget = 0f

    internal var listeningKeybind: KeybindSetting? = null
    internal var draggingSlider: NumberSetting? = null
    internal var draggingRange: RangeSetting? = null
    internal var draggingRangeHigh = false
    internal var sliderBaseX = 0
    internal var sliderBaseW = 0
    internal var draggingColorSB: ColorSetting? = null
    internal var draggingColorHue: ColorSetting? = null
    internal var draggingColorAlpha: ColorSetting? = null
    internal var colorPickerBaseX = 0
    internal var colorPickerBaseW = 0
    internal var colorPickerSBTop = 0
    internal var colorPickerSBH = 0
    internal var hexEditSetting: ColorSetting? = null
    internal var hexInput = ""
    internal var numberEditSetting: NumberSetting? = null
    internal var numberInput = ""

    internal var tooltipText: String? = null
    internal var tooltipX = 0
    internal var tooltipY = 0

    override fun isPauseScreen() = false

    override fun init() {
        super.init()
        recomputeScale()
    }

    private fun recomputeScale() {
        val targetW = width * 0.8f
        val targetH = height * 0.8f
        guiScale = minOf(targetW / PANEL_W, targetH / PANEL_H).coerceAtLeast(0.5f)
        drawOffsetX = ((width - PANEL_W * guiScale) / 2f).toInt()
        drawOffsetY = ((height - PANEL_H * guiScale) / 2f).toInt()
    }

    fun toGuiX(screenX: Double): Int = ((screenX - drawOffsetX) / guiScale).toInt()
    fun toGuiY(screenY: Double): Int = ((screenY - drawOffsetY) / guiScale).toInt()

    override fun onClose() {
        lastCategory = currentCategory
        lastSettingsModule = settingsModule
        super.onClose()
    }

    fun openSettings(module: Module) {
        settingsModule = module
        scrollOffset = 0f
        scrollTarget = 0f
        listeningKeybind = null
        numberEditSetting = null
        hexEditSetting = null
    }

    fun closeSettings() {
        settingsModule = null
        scrollOffset = 0f
        scrollTarget = 0f
        listeningKeybind = null
        numberEditSetting = null
        hexEditSetting = null
    }

    fun visibleModules(): List<Module> {
        val q = searchQuery.lowercase().trim()
        if (q.isEmpty()) return Module.getByCategory(currentCategory)
        return Module.modules.filter {
            !it.hidden && (it.name.lowercase().contains(q) || it.description.lowercase().contains(q))
        }
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(context, mouseX, mouseY, delta)
        if (width != lastWidth || height != lastHeight) {
            recomputeScale()
            lastWidth = width
            lastHeight = height
        }

        fill(context, 0, 0, width, height, 0x80000000.toInt())

        val gmx = toGuiX(mouseX.toDouble())
        val gmy = toGuiY(mouseY.toDouble())

        context.pose().pushMatrix()
        context.pose().translate(drawOffsetX.toFloat(), drawOffsetY.toFloat())
        context.pose().scale(guiScale, guiScale)

        drawChrome(context)
        SearchComponent.draw(context, this, gmx, gmy)

        val mod = settingsModule
        if (mod != null) ModuleSettingsComponent.draw(context, this, mod, gmx, gmy)
        else ModuleGridComponent.draw(context, this, gmx, gmy)

        drawContentDim(context)
        SidebarComponent.draw(context, this, gmx, gmy)

        tooltipText?.let { SearchComponent.drawTooltip(context, this, it) }
        tooltipText = null

        context.pose().popMatrix()

        scrollOffset += (scrollTarget - scrollOffset) * 0.35f
    }

    private var lastWidth = -1
    private var lastHeight = -1

    private fun drawChrome(ctx: GuiGraphicsExtractor) {
        val target = if (sidebarExpanded) 1f else 0f
        sidebarExpand += (target - sidebarExpand) * 0.25f
        sidebarExpand = sidebarExpand.coerceIn(0f, 1f)

        val x = panelX
        val y = panelY

        fill(ctx, x - 1, y - 1, PANEL_W + 2, PANEL_H + 2, cBorder)
        roundedFill(ctx, x, y, PANEL_W, PANEL_H, cPanelBg)

        val headerX = x + SIDEBAR_W_COLLAPSED
        val headerW = PANEL_W - SIDEBAR_W_COLLAPSED
        fill(ctx, headerX, y + 1, headerW - 1, HEADER_H - 1, cHeaderBg)
        fill(ctx, headerX + 4, y + HEADER_H, headerW - 8, 1, cBorderLight)
    }

    private fun drawContentDim(ctx: GuiGraphicsExtractor) {
        if (sidebarExpand <= 0.01f) return
        val alpha = (sidebarExpand * 0.55f * 255f).toInt().coerceIn(0, 255)
        fill(ctx, panelX + SIDEBAR_W_COLLAPSED, panelY + 1, PANEL_W - SIDEBAR_W_COLLAPSED - 1, PANEL_H - 2, alpha shl 24)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (InputHandler.handleMouseClick(this, toGuiX(click.x()), toGuiY(click.y()), click.button())) return true
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        if (InputHandler.handleMouseDrag(this, toGuiX(click.x()).toDouble(), toGuiY(click.y()).toDouble())) return true
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        InputHandler.handleMouseRelease(this)
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (InputHandler.handleScroll(this, toGuiX(mouseX), toGuiY(mouseY), verticalAmount)) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (InputHandler.handleKeyPress(this, input.key())) return true
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharacterEvent): Boolean {
        if (InputHandler.handleCharTyped(this, input.codepoint().toChar())) return true
        return super.charTyped(input)
    }
}
