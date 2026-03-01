package gobby.gui.click

import gobby.gui.click.ClickGUITheme.COLOR_PICKER_H
import gobby.gui.click.ClickGUITheme.GAP
import gobby.gui.click.ClickGUITheme.HH
import gobby.gui.click.ClickGUITheme.MH
import gobby.gui.click.ClickGUITheme.PW
import gobby.gui.click.ClickGUITheme.SH
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text

class ClickGUI : Screen(Text.literal("GobbyClient")) {

    companion object {
        val panelPositions = mutableMapOf<Category, Pair<Float, Float>>()
    }

    class PanelState(
        val category: Category,
        var x: Float = 0f,
        var y: Float = 0f,
        var collapsed: Boolean = false,
        var scrollOffset: Float = 0f,
        var dragging: Boolean = false,
        var dragOffsetX: Double = 0.0,
        var dragOffsetY: Double = 0.0
    )

    // State — internal so helper objects in the same package can access
    internal val panels = mutableListOf<PanelState>()
    internal var searchQuery = ""
    internal var searchListening = false
    internal var listeningKeybind: KeybindSetting? = null
    internal var suppressNextChar = false
    internal var draggingSlider: NumberSetting? = null
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

    override fun shouldPause() = false

    override fun init() {
        super.init()
        if (panels.isEmpty()) {
            val cats = Category.entries
            val totalW = cats.size * PW + (cats.size - 1) * GAP
            val defaultStartX = ((width - totalW) / 2f).coerceAtLeast(4f)
            cats.forEachIndexed { i, cat ->
                val saved = panelPositions[cat]
                val sx = saved?.first ?: (defaultStartX + i * (PW + GAP))
                val sy = saved?.second ?: 10f
                panels.add(PanelState(cat, sx, sy))
            }
        }
    }

    override fun close() {
        for (panel in panels) {
            panelPositions[panel.category] = Pair(panel.x, panel.y)
        }
        ConfigManager.save()
        super.close()
    }

    // ===== Rendering =====

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        for (panel in panels) PanelRenderer.drawPanel(context, this, panel, mouseX, mouseY)
        SearchBarRenderer.drawSearchBar(context, this, mouseX, mouseY)
        tooltipText?.let { SearchBarRenderer.drawTooltip(context, this, it) }
        tooltipText = null
    }

    // ===== Helpers used by renderers =====

    internal fun settingHeight(s: Setting<*>): Int {
        if (s is ColorSetting && s.expanded) return SH + COLOR_PICKER_H
        return SH
    }

    internal fun settingsHeight(m: Module): Int {
        return m.allSettings().filter { it.isVisible }.sumOf { settingHeight(it) }
    }

    internal fun getModulesForPanel(panel: PanelState): List<Module> {
        val query = searchQuery.lowercase().trim()
        val catModules = Module.getByCategory(panel.category)
        if (query.isEmpty()) return catModules
        return catModules.filter { it.name.lowercase().contains(query) || it.description.lowercase().contains(query) }
    }

    // ===== Input handling — delegates to InputHandler =====

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        val button = click.button()
        if (InputHandler.handleMouseClick(this, mx, my, button)) return true
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        val currentX = click.x() + offsetX
        val currentY = click.y() + offsetY
        if (InputHandler.handleMouseDrag(this, currentX, currentY)) return true
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        InputHandler.handleMouseRelease(this)
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (InputHandler.handleScroll(this, mouseX.toInt(), mouseY.toInt(), verticalAmount)) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (InputHandler.handleKeyPress(this, input.key())) return true
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (InputHandler.handleCharTyped(this, input.codepoint().toChar())) return true
        return super.charTyped(input)
    }
}
