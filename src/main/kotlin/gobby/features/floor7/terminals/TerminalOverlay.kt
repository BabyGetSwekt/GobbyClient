package gobby.features.floor7.terminals

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.gui.ScreenRenderEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.features.floor7.terminals.AutoTerminals
import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Formatting
import org.lwjgl.glfw.GLFW
import java.awt.Color

object TerminalOverlay : Module(
    "Terminal Overlay", "Custom terminal overlay",
    Category.FLOOR7
) {
    val scale by NumberSetting("Scale", 150, 100, 300, 10, desc = "Scale of the overlay UI (percent)")

    private const val CELL = 16
    private const val STRIDE = 18
    private const val BG_PAD = 2
    private const val BORDER_W = 2
    private const val CHEST_ROW_WIDTH = 9
    private const val PLAYER_INV_SLOTS = 36
    private const val SCALE_BASE = 100f
    private const val SCALE_MULT = 1.5f
    private const val NUM_HIGHLIGHT_COUNT = 3
    private const val MELODY_BTN_CHEST_COL = 7
    private const val MELODY_BTN_COMPACT_COL = 6
    private const val MELODY_GAP_COMPACT_COL = 5
    private const val MELODY_LANE_START = 1
    private const val MELODY_LANE_END = 5
    private const val MELODY_ROW_START = 1
    private const val MELODY_ROW_END = 4

    private val cOverlay = Color(0, 0, 0, 140).rgb
    private val cBg = Color(15, 15, 22, 220).rgb
    private val cBorder = Color(0, 170, 0, 255).rgb
    private val cSolution = Color(0, 170, 0, 200).rgb
    private val cNum1 = Color(0, 255, 0, 200).rgb
    private val cNum2 = Color(0, 180, 0, 200).rgb
    private val cNum3 = Color(0, 110, 0, 200).rgb
    private val cRubixL = Color(0, 200, 0, 200).rgb
    private val cRubixR = Color(200, 50, 50, 200).rgb
    private val cTxtMain = Color(220, 220, 230).rgb
    private val cMelodyCol = Color(80, 150, 255, 80).rgb
    private val cMelodyBtnOn = Color(0, 230, 0, 200).rgb
    private val cMelodyBtnOff = Color(90, 90, 100, 150).rgb
    private val cMelodyPane = Color(0, 200, 80, 180).rgb
    private val cMelodyIndicator = Color(180, 50, 180, 200).rgb

    private var mouseWasDown = false
    private var gridOffX = 0
    private var gridOffY = 0
    private var gridCols = 0
    private var gridRows = 0

    private enum class Terminal {
        NUMBERS, COLORS, STARTS_WITH, RED_GREEN, RUBIX, MELODY
    }

    private data class GridConfig(val cols: Int, val rows: Int, val rowStart: Int, val colStart: Int)

    private fun gridConfig(type: Terminal) = when (type) {
        Terminal.NUMBERS     -> GridConfig(7, 2, 1, 1)
        Terminal.COLORS      -> GridConfig(7, 4, 1, 1)
        Terminal.STARTS_WITH -> GridConfig(7, 3, 1, 1)
        Terminal.RED_GREEN   -> GridConfig(5, 3, 1, 2)
        Terminal.RUBIX       -> GridConfig(3, 3, 1, 3)
        Terminal.MELODY      -> GridConfig(7, 5, 0, 1)
    }

    private fun compactToSlot(type: Terminal, cx: Int, cy: Int): Int {
        val cfg = gridConfig(type)
        if (type == Terminal.MELODY) {
            val chestRow = cy + cfg.rowStart
            val chestCol = if (cx == MELODY_BTN_COMPACT_COL) MELODY_BTN_CHEST_COL else cx + cfg.colStart
            return chestRow * CHEST_ROW_WIDTH + chestCol
        }
        val chestRow = cy + cfg.rowStart
        val chestCol = cx + cfg.colStart
        return chestRow * CHEST_ROW_WIDTH + chestCol
    }

    private fun slotToCompact(type: Terminal, slot: Int): Pair<Int, Int>? {
        val cfg = gridConfig(type)
        val chestRow = slot / CHEST_ROW_WIDTH
        val chestCol = slot % CHEST_ROW_WIDTH
        val cy = chestRow - cfg.rowStart
        if (cy < 0 || cy >= cfg.rows) return null
        if (type == Terminal.MELODY) {
            val cx = if (chestCol == MELODY_BTN_CHEST_COL) MELODY_BTN_COMPACT_COL else chestCol - cfg.colStart
            if (cx < 0 || cx > MELODY_BTN_COMPACT_COL || cx == MELODY_GAP_COMPACT_COL) return null
            return cx to cy
        }
        val cx = chestCol - cfg.colStart
        if (cx < 0 || cx >= cfg.cols) return null
        return cx to cy
    }

    private fun compactPos(cx: Int, cy: Int): Pair<Int, Int> =
        (cx * STRIDE + gridOffX) to (cy * STRIDE + gridOffY)

    fun isOverlayActive(): Boolean {
        if (!enabled) return false
        val screen = mc.currentScreen as? GenericContainerScreen ?: return false
        return detect(screen.title.string) != null
    }

    private fun detect(title: String): Terminal? = when {
        title.contains("Click in order!") -> Terminal.NUMBERS
        Regex("Select all the [\\w ]+ items!").containsMatchIn(Formatting.strip(title) ?: "") -> Terminal.COLORS
        Regex("What starts with: \\W\\w\\W").containsMatchIn(Formatting.strip(title) ?: "") -> Terminal.STARTS_WITH
        title.contains("Correct all the panes!") -> Terminal.RED_GREEN
        title.contains("Change all to same color!") -> Terminal.RUBIX
        title.contains("Click the button on time!") -> Terminal.MELODY
        else -> null
    }

    @SubscribeEvent
    fun onScreenRender(event: ScreenRenderEvent) {
        if (!enabled) return
        val screen = event.screen as? GenericContainerScreen ?: return
        val type = detect(screen.title.string) ?: return

        val ctx = event.drawContext
        val sw = mc.window.scaledWidth
        val sh = mc.window.scaledHeight
        val uiScale = scale / SCALE_BASE * SCALE_MULT

        val cfg = gridConfig(type)
        gridCols = cfg.cols
        gridRows = cfg.rows
        val gridW = gridCols * STRIDE
        val gridH = gridRows * STRIDE
        val logW = (sw / uiScale).toInt()
        val logH = (sh / uiScale).toInt()
        gridOffX = logW / 2 - gridW / 2
        gridOffY = logH / 2 - gridH / 2

        val lmx = (event.mouseX / uiScale).toInt()
        val lmy = (event.mouseY / uiScale).toInt()
        handleMouse(lmx, lmy, screen, type)

        ctx.fill(0, 0, sw, sh, cOverlay)

        ctx.matrices.pushMatrix()
        ctx.matrices.scale(uiScale, uiScale)

        val bx0 = gridOffX - BG_PAD - BORDER_W
        val by0 = gridOffY - BG_PAD - BORDER_W
        val bx1 = gridOffX + gridW + BG_PAD + BORDER_W
        val by1 = gridOffY + gridH + BG_PAD + BORDER_W

        ctx.fill(bx0, by0, bx1, by0 + BORDER_W, cBorder)
        ctx.fill(bx0, by1 - BORDER_W, bx1, by1, cBorder)
        ctx.fill(bx0, by0 + BORDER_W, bx0 + BORDER_W, by1 - BORDER_W, cBorder)
        ctx.fill(bx1 - BORDER_W, by0 + BORDER_W, bx1, by1 - BORDER_W, cBorder)

        ctx.fill(bx0 + BORDER_W, by0 + BORDER_W, bx1 - BORDER_W, by1 - BORDER_W, cBg)

        when (type) {
            Terminal.NUMBERS -> drawNumbers(ctx, screen)
            Terminal.COLORS -> drawColors(ctx, screen)
            Terminal.STARTS_WITH -> drawStartsWith(ctx, screen)
            Terminal.RED_GREEN -> drawRedGreen(ctx, screen)
            Terminal.RUBIX -> drawRubix(ctx, screen)
            Terminal.MELODY -> drawMelody(ctx, screen)
        }

        ctx.matrices.popMatrix()
    }

    private fun handleMouse(lmx: Int, lmy: Int, screen: GenericContainerScreen, type: Terminal) {
        val down = GLFW.glfwGetMouseButton(mc.window.handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        if (type == Terminal.RUBIX && !AutoTerminals.enabled && mouseWasDown && !down) {
            val cx = if (lmx >= gridOffX) (lmx - gridOffX) / STRIDE else -1
            val cy = if (lmy >= gridOffY) (lmy - gridOffY) / STRIDE else -1
            if (cx in 0 until gridCols && cy in 0 until gridRows) {
                val slot = compactToSlot(type, cx, cy)
                val idx = TerminalUtils.RUBIX_SLOTS.indexOf(slot)
                if (idx != -1) {
                    val solution = RubixTerminal.getFullSolution(screen)
                    if (solution != null) {
                        val clicks = solution[idx]
                        val button = when {
                            clicks > 0 -> 0
                            clicks < 0 -> 1
                            else -> -1
                        }
                        if (button >= 0) {
                            mc.interactionManager?.clickSlot(
                                screen.screenHandler.syncId, slot, button, SlotActionType.PICKUP, mc.player
                            )
                        }
                    }
                }
            }
        }
        mouseWasDown = down
    }

    private fun drawNumbers(ctx: DrawContext, screen: GenericContainerScreen) {
        val handler = screen.screenHandler
        val slots = TerminalUtils.NUMBERS_SLOTS

        val solution = slots.filter { s ->
            val st = handler.slots[s].stack
            !st.isEmpty && !TerminalUtils.isTerminalItemDone(st) && st.item == Items.RED_STAINED_GLASS_PANE
        }.sortedBy { handler.slots[it].stack.count }

        val colors = intArrayOf(cNum1, cNum2, cNum3)
        for ((rank, slot) in solution.withIndex()) {
            if (rank >= NUM_HIGHLIGHT_COUNT) break
            val (cx, cy) = slotToCompact(Terminal.NUMBERS, slot) ?: continue
            val (x, y) = compactPos(cx, cy)
            ctx.fill(x, y, x + CELL, y + CELL, colors[rank])
        }

        val fullOrder = slots.filter { !handler.slots[it].stack.isEmpty }
            .sortedBy { handler.slots[it].stack.count }
        for (slot in fullOrder) {
            val stack = handler.slots[slot].stack
            val (cx, cy) = slotToCompact(Terminal.NUMBERS, slot) ?: continue
            val (x, y) = compactPos(cx, cy)
            val num = "${stack.count}"
            val tw = mc.textRenderer.getWidth(num)
            ctx.drawText(mc.textRenderer, num, x + (CELL - tw) / 2, y + (CELL - mc.textRenderer.fontHeight) / 2, cTxtMain, true)
        }
    }

    private fun drawColors(ctx: DrawContext, screen: GenericContainerScreen) {
        val handler = screen.screenHandler
        val stripped = Formatting.strip(screen.title.string) ?: return
        val color = Regex("Select all the ([\\w ]+) items!").find(stripped)
            ?.groupValues?.get(1)?.lowercase()?.trim() ?: return

        for (slot in TerminalUtils.COLORS_SLOTS) {
            val stack = handler.slots[slot].stack
            if (stack.isEmpty || TerminalUtils.isTerminalItemDone(stack)) continue
            val name = TerminalUtils.normalizeItemName(Formatting.strip(stack.name.string)?.lowercase()?.trim() ?: "")
            if (!name.startsWith(color)) continue
            val (cx, cy) = slotToCompact(Terminal.COLORS, slot) ?: continue
            val (x, y) = compactPos(cx, cy)
            ctx.fill(x, y, x + CELL, y + CELL, cSolution)
        }
    }

    private fun drawStartsWith(ctx: DrawContext, screen: GenericContainerScreen) {
        val handler = screen.screenHandler
        val stripped = Formatting.strip(screen.title.string) ?: return
        val letter = Regex("What starts with: \\W(\\w)\\W").find(stripped)
            ?.groupValues?.get(1)?.lowercase() ?: return

        for (slot in TerminalUtils.STARTS_WITH_SLOTS) {
            val stack = handler.slots[slot].stack
            if (stack.isEmpty || TerminalUtils.isTerminalItemDone(stack)) continue
            val name = Formatting.strip(stack.name.string)?.trim()?.lowercase() ?: ""
            if (name.isEmpty() || !name.startsWith(letter)) continue
            val (cx, cy) = slotToCompact(Terminal.STARTS_WITH, slot) ?: continue
            val (x, y) = compactPos(cx, cy)
            ctx.fill(x, y, x + CELL, y + CELL, cSolution)
        }
    }

    private fun drawRedGreen(ctx: DrawContext, screen: GenericContainerScreen) {
        val handler = screen.screenHandler
        for (slot in TerminalUtils.RED_GREEN_SLOTS) {
            if (handler.slots[slot].stack.item != Items.RED_STAINED_GLASS_PANE) continue
            val (cx, cy) = slotToCompact(Terminal.RED_GREEN, slot) ?: continue
            val (x, y) = compactPos(cx, cy)
            ctx.fill(x, y, x + CELL, y + CELL, cSolution)
        }
    }

    private fun drawRubix(ctx: DrawContext, screen: GenericContainerScreen) {
        val solution = RubixTerminal.getFullSolution(screen) ?: return

        for ((idx, slot) in TerminalUtils.RUBIX_SLOTS.withIndex()) {
            val clicks = solution[idx]
            if (clicks == 0) continue
            val (cx, cy) = slotToCompact(Terminal.RUBIX, slot) ?: continue
            val (x, y) = compactPos(cx, cy)
            ctx.fill(x, y, x + CELL, y + CELL, if (clicks > 0) cRubixL else cRubixR)
            val text = "$clicks"
            val tw = mc.textRenderer.getWidth(text)
            ctx.drawText(mc.textRenderer, text, x + (CELL - tw) / 2, y + (CELL - mc.textRenderer.fontHeight) / 2, cTxtMain, true)
        }
    }

    private fun drawMelody(ctx: DrawContext, screen: GenericContainerScreen) {
        val handler = screen.screenHandler
        val containerSlots = handler.slots.size - PLAYER_INV_SLOTS

        val targetChestCol = (MELODY_LANE_START..MELODY_LANE_END).firstOrNull {
            handler.slots[it].stack.item == Items.MAGENTA_STAINED_GLASS_PANE
        } ?: -1
        val targetCompact = if (targetChestCol in MELODY_LANE_START..MELODY_LANE_END) targetChestCol - MELODY_LANE_START else -1

        var correctBtnRow = -1
        var currentPaneSlot = -1

        if (targetChestCol >= 0) {
            for (slot in CHEST_ROW_WIDTH until containerSlots) {
                if (handler.slots[slot].stack.item == Items.LIME_STAINED_GLASS_PANE) {
                    val col = slot % CHEST_ROW_WIDTH
                    if (col == targetChestCol) {
                        correctBtnRow = slot / CHEST_ROW_WIDTH
                    }
                    currentPaneSlot = slot
                }
            }
        }

        val laneRange = 0..(MELODY_LANE_END - MELODY_LANE_START)

        if (targetCompact in laneRange) {
            val (ix, iy) = compactPos(targetCompact, 0)
            ctx.fill(ix, iy, ix + CELL, iy + CELL, cMelodyIndicator)
        }

        if (targetCompact in laneRange) {
            val (hx, _) = compactPos(targetCompact, MELODY_ROW_START)
            val (_, topY) = compactPos(0, MELODY_ROW_START)
            val bottomY = gridOffY + gridRows * STRIDE - BG_PAD
            ctx.fill(hx, topY, hx + CELL, bottomY, cMelodyCol)
        }

        for (row in MELODY_ROW_START..MELODY_ROW_END) {
            val (bx, by) = compactPos(MELODY_BTN_COMPACT_COL, row)
            ctx.fill(bx, by, bx + CELL, by + CELL, if (row == correctBtnRow) cMelodyBtnOn else cMelodyBtnOff)
        }

        if (currentPaneSlot >= 0) {
            val paneChestCol = currentPaneSlot % CHEST_ROW_WIDTH
            val paneRow = currentPaneSlot / CHEST_ROW_WIDTH
            val paneCompact = paneChestCol - MELODY_LANE_START
            if (paneCompact in laneRange && paneRow in MELODY_ROW_START..MELODY_ROW_END) {
                val (px, py) = compactPos(paneCompact, paneRow)
                ctx.fill(px, py, px + CELL, py + CELL, cMelodyPane)
            }
        }
    }
}
