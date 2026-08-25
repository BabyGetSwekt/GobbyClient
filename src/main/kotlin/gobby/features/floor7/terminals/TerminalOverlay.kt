package gobby.features.floor7.terminals

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.gui.ScreenRenderEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.inventory.ContainerInput
import org.lwjgl.glfw.GLFW
import java.awt.Color

object TerminalOverlay : Module("Terminal Overlay", "Custom terminal overlay", Category.FLOOR7) {
    val scale by NumberSetting("Scale", 150, 100, 300, 10, desc = "Scale of the overlay UI (percent)")
    private const val STRIDE = 18
    private const val BG_PAD = 2
    private const val BORDER_W = 2
    private const val SCALE_BASE = 100f
    private const val SCALE_MULT = 1.5f
    private val cOverlay = Color(0, 0, 0, 140).rgb
    private val cBg = Color(15, 15, 22, 220).rgb
    private val cBorder = Color(0, 170, 0, 255).rgb
    private var mouseWasDown = false
    private var rightMouseWasDown = false
    private var gridOffX = 0
    private var gridOffY = 0
    private var gridCols = 0
    private var gridRows = 0

    fun isOverlayActive(): Boolean {
        if (!enabled) return false
        val screen = mc.gui.screen() as? ContainerScreen ?: return false
        return detect(screen.title.string) != null
    }

    fun shouldBlockClicks(): Boolean = isOverlayActive()

    @SubscribeEvent
    fun onScreenRender(event: ScreenRenderEvent) {
        if (!enabled) return
        val screen = event.screen as? ContainerScreen ?: return
        val type = detect(screen.title.string) ?: return
        val uiScale = scale / SCALE_BASE * SCALE_MULT
        configureLayout(type, mc.window.guiScaledWidth, mc.window.guiScaledHeight, uiScale)
        handleMouse((event.mouseX / uiScale).toInt(), (event.mouseY / uiScale).toInt(), screen, type)
        val ctx = event.drawContext
        ctx.fill(0, 0, mc.window.guiScaledWidth, mc.window.guiScaledHeight, cOverlay)
        ctx.pose().pushMatrix()
        ctx.pose().scale(uiScale, uiScale)
        drawPanel(ctx, type, screen)
        ctx.pose().popMatrix()
    }

    private fun configureLayout(type: TerminalType, screenWidth: Int, screenHeight: Int, uiScale: Float) {
        val config = TerminalOverlayLayout.gridConfig(type)
        gridCols = config.cols
        gridRows = config.rows
        val width = gridCols * STRIDE
        val height = gridRows * STRIDE
        val logicalWidth = (screenWidth / uiScale).toInt()
        val logicalHeight = (screenHeight / uiScale).toInt()
        gridOffX = logicalWidth / 2 - width / 2
        gridOffY = logicalHeight / 2 - height / 2
    }

    private fun drawPanel(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, type: TerminalType, screen: ContainerScreen) {
        val width = gridCols * STRIDE
        val height = gridRows * STRIDE
        val left = gridOffX - BG_PAD - BORDER_W
        val top = gridOffY - BG_PAD - BORDER_W
        val right = gridOffX + width + BG_PAD + BORDER_W
        val bottom = gridOffY + height + BG_PAD + BORDER_W
        ctx.fill(left, top, right, top + BORDER_W, cBorder)
        ctx.fill(left, bottom - BORDER_W, right, bottom, cBorder)
        ctx.fill(left, top + BORDER_W, left + BORDER_W, bottom - BORDER_W, cBorder)
        ctx.fill(right - BORDER_W, top + BORDER_W, right, bottom - BORDER_W, cBorder)
        ctx.fill(left + BORDER_W, top + BORDER_W, right - BORDER_W, bottom - BORDER_W, cBg)
        TerminalOverlayRenderer.draw(type, ctx, screen, gridOffX, gridOffY, gridRows)
    }

    private fun handleMouse(lmx: Int, lmy: Int, screen: ContainerScreen, type: TerminalType) {
        val leftDown = GLFW.glfwGetMouseButton(mc.window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val rightDown = GLFW.glfwGetMouseButton(mc.window.handle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
        val leftReleased = mouseWasDown && !leftDown
        val rightReleased = rightMouseWasDown && !rightDown
        mouseWasDown = leftDown
        rightMouseWasDown = rightDown
        if (AutoTerminals.enabled || (!leftReleased && !rightReleased)) return
        val cx = if (lmx >= gridOffX) (lmx - gridOffX) / STRIDE else -1
        val cy = if (lmy >= gridOffY) (lmy - gridOffY) / STRIDE else -1
        if (cx !in 0 until gridCols || cy !in 0 until gridRows) return
        val slot = TerminalOverlayLayout.compactToSlot(type, cx, cy)
        val player = mc.player ?: return
        if (type == TerminalType.RUBIX) clickRubix(screen, slot, leftReleased, rightReleased, player)
        else mc.gameMode?.handleContainerInput(screen.menu.containerId, slot, 2, ContainerInput.CLONE, player)
    }

    private fun clickRubix(screen: ContainerScreen, slot: Int, leftReleased: Boolean, rightReleased: Boolean, player: net.minecraft.client.player.LocalPlayer) {
        val index = TerminalUtils.RUBIX_SLOTS.indexOf(slot)
        val clicks = RubixTerminal.getFullSolution(screen)?.getOrNull(index) ?: return
        val button = when {
            leftReleased && clicks > 0 || rightReleased && clicks < 0 -> 0
            leftReleased && clicks < 0 || rightReleased && clicks > 0 -> 1
            else -> -1
        }
        if (button >= 0) mc.gameMode?.handleContainerInput(screen.menu.containerId, slot, button, ContainerInput.PICKUP, player)
    }

    private fun detect(title: String): TerminalType? = when {
        title.contains("Click in order!") -> TerminalType.NUMBERS
        COLORS_REGEX.containsMatchIn(net.minecraft.ChatFormatting.stripFormatting(title) ?: "") -> TerminalType.COLORS
        STARTS_WITH_REGEX.containsMatchIn(net.minecraft.ChatFormatting.stripFormatting(title) ?: "") -> TerminalType.STARTS_WITH
        title.contains("Correct all the panes!") -> TerminalType.RED_GREEN
        title.contains("Change all to same color!") -> TerminalType.RUBIX
        title.contains("Click the button on time!") -> TerminalType.MELODY
        else -> null
    }

    private val COLORS_REGEX = Regex("Select all the [\\w ]+ items!")
    private val STARTS_WITH_REGEX = Regex("What starts with: \\W\\w\\W")
}
