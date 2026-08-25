package gobby.gui.click

import gobby.utils.render.Animations
import gobby.utils.render.CursorStyle
import kotlin.math.abs
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.*
import net.minecraft.network.chat.Component

private const val FLIP_MS = 170L

class ClickGUI : Screen(Component.literal("GobbyClient")) {

    companion object {
        var lastCategory: Category? = null
        var lastSettingsModule: Module? = null
    }

    internal var guiScale = 1f

    internal var drawOffsetX = 0
    internal var drawOffsetY = 0
    internal val frame: PanelFrame get() = PanelFrame(panelX, panelY)
    internal val panelX = 0
    internal val panelY = 0
    internal val contentX: Int get() = panelX + SIDEBAR_W_SETTINGS + SETTINGS_SIDE_PAD
    internal val contentY: Int get() = panelY + SETTINGS_HEADER_H + SETTINGS_SIDE_PAD
    internal val contentW: Int get() = PANEL_W - SIDEBAR_W_SETTINGS - SETTINGS_SIDE_PAD * 2
    internal val contentH: Int get() = PANEL_H - SETTINGS_HEADER_H - SETTINGS_SIDE_PAD * 2

    internal var currentCategory: Category = lastCategory ?: Category.entries.first()
    internal var settingsModule: Module? = lastSettingsModule

    internal var suppressNextChar = false
    internal var draggingSearch = false

    private val flips = Animations(FLIP_MS)

    internal fun flip(key: Any, open: Boolean): Float = flips.toward(key, open).value

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
    internal var openSelector: SelectorSetting? = null
    internal var draggingPreview = false
    internal var hexEditSetting: ColorSetting? = null
    internal val hexField = TextField(HexColor::sanitize, HexColor.MAX_LENGTH)
    internal var draggingHex = false
    internal var numberEditSetting: NumberSetting? = null
    internal val numberField = TextField(NumberInput::sanitize, NumberInput.MAX_LENGTH)
    internal var stringEditSetting: StringSetting? = null
    internal val stringField = TextField(StringInput::sanitize, StringInput.DEFAULT_MAX_LENGTH)

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
        SearchBar.close()
        SelectorPopup.forget()
        CursorStyle.reset()
        lastCategory = currentCategory
        lastSettingsModule = settingsModule
        super.onClose()
    }

    fun openSettings(module: Module) {
        SearchBar.close()
        SelectorPopup.forget()
        settingsModule = module
        scrollOffset = 0f
        scrollTarget = 0f
        listeningKeybind = null
        numberEditSetting = null
        hexEditSetting = null
        openSelector = null
        draggingPreview = false
        draggingHex = false
        stringEditSetting = null
    }

    fun closeSettings() {
        SelectorPopup.forget()
        settingsModule = null
        scrollOffset = 0f
        scrollTarget = 0f
        listeningKeybind = null
        numberEditSetting = null
        hexEditSetting = null
        openSelector = null
        draggingPreview = false
        draggingHex = false
        stringEditSetting = null
    }

    fun visibleModules(): List<Module> {
        val q = SearchBar.query.lowercase().trim()
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

        extractBlurredBackground(context)
        fill(context, 0, 0, width, height, 0x66000000.toInt())

        val gmx = toGuiX(mouseX.toDouble())
        val gmy = toGuiY(mouseY.toDouble())

        context.pose().pushMatrix()
        context.pose().translate(drawOffsetX.toFloat(), drawOffsetY.toFloat())
        context.pose().scale(guiScale, guiScale)

        val mod = settingsModule
        if (mod != null) {
            drawSettingsShell(context)
            SettingsSidebar.draw(context, this, gmx, gmy)
            ModuleSettingsComponent.draw(context, this, mod, gmx, gmy)
        } else {
            drawSettingsShell(context)
            SettingsSidebar.draw(context, this, gmx, gmy)
            SettingsHeader.drawGrid(context, this, gmx, gmy)
            ModuleGridComponent.draw(context, this, gmx, gmy)
        }

        tooltipText?.let { Tooltip.draw(context, this, it) }
        tooltipText = null

        context.pose().popMatrix()
        CursorStyle.apply()

        scrollOffset += (scrollTarget - scrollOffset) * SCROLL_EASE
        if (abs(scrollTarget - scrollOffset) < SCROLL_SNAP) scrollOffset = scrollTarget
    }

    private var lastWidth = -1
    private var lastHeight = -1

    private fun drawSettingsShell(ctx: GuiGraphicsExtractor) {
        GobbyDraw.roundedBox(ctx, panelX, panelY, PANEL_W, PANEL_H, SETTINGS_PANEL_RADIUS, cShellBg, cShellEdge)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (InputHandler.handleMouseClick(this, toGuiX(click.x()), toGuiY(click.y()), click.button())) return true
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        if (draggingSearch && SearchBar.handleDrag(this, toGuiX(click.x()))) return true
        if (draggingHex) {
            settingsModule?.let { ModuleSettingsComponent.handleHexDrag(this, it, toGuiX(click.x())) }
            return true
        }
        if (draggingPreview) {
            SettingsPreview.rotate(offsetX / guiScale, offsetY / guiScale)
            return true
        }
        if (InputHandler.handleMouseDrag(this, toGuiX(click.x()).toDouble(), toGuiY(click.y()).toDouble())) return true
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        draggingPreview = false
        draggingHex = false
        draggingSearch = false
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
