package gobby.features.floor7.terminals

import gobby.Gobbyclient.Companion.mc
import gobby.utils.skyblock.dungeon.TerminalUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW
import net.minecraft.ChatFormatting
import java.awt.Color

internal object TerminalOverlayRenderer {
    private const val CELL = 16
    private const val STRIDE = 18
    private const val CHEST_ROW_WIDTH = 9
    private const val PLAYER_INV_SLOTS = 36
    private const val NUM_HIGHLIGHT_COUNT = 3
    private const val MELODY_LANE_START = 1
    private const val MELODY_LANE_END = 5
    private const val MELODY_BTN_COMPACT_COL = 6
    private const val MELODY_ROW_START = 1
    private const val MELODY_ROW_END = 4
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

    fun draw(type: TerminalType, ctx: GuiGraphicsExtractor, screen: ContainerScreen, gridOffX: Int, gridOffY: Int, gridRows: Int) {
        when (type) {
            TerminalType.NUMBERS -> drawNumbers(ctx, screen, gridOffX, gridOffY)
            TerminalType.COLORS -> drawColors(ctx, screen, gridOffX, gridOffY)
            TerminalType.STARTS_WITH -> drawStartsWith(ctx, screen, gridOffX, gridOffY)
            TerminalType.RED_GREEN -> drawRedGreen(ctx, screen, gridOffX, gridOffY)
            TerminalType.RUBIX -> drawRubix(ctx, screen, gridOffX, gridOffY)
            TerminalType.MELODY -> drawMelody(ctx, screen, gridOffX, gridOffY, gridRows)
        }
    }

    private fun drawNumbers(ctx: GuiGraphicsExtractor, screen: ContainerScreen, offX: Int, offY: Int) {
        val slots = TerminalUtils.NUMBERS_SLOTS
        val solution = slots.filter { slot ->
            val stack = screen.menu.slots[slot].item
            !stack.isEmpty && !TerminalUtils.isTerminalItemDone(stack) && stack.item == Items.STAINED_GLASS_PANE.red()
        }.sortedBy { screen.menu.slots[it].item.count }
        val colors = intArrayOf(cNum1, cNum2, cNum3)
        solution.take(NUM_HIGHLIGHT_COUNT).forEachIndexed { rank, slot ->
            drawCell(ctx, TerminalType.NUMBERS, slot, colors[rank], offX, offY)
        }
        slots.filter { !screen.menu.slots[it].item.isEmpty }
            .sortedBy { screen.menu.slots[it].item.count }
            .forEach { slot ->
                val stack = screen.menu.slots[slot].item
                val position = TerminalOverlayLayout.slotToCompact(TerminalType.NUMBERS, slot) ?: return@forEach
                val (x, y) = compactPosition(position.first, position.second, offX, offY)
                val text = stack.count.toString()
                val width = mc.font.width(text)
                ctx.text(mc.font, text, x + (CELL - width) / 2, y + (CELL - mc.font.lineHeight) / 2, cTxtMain, true)
            }
    }

    private fun drawColors(ctx: GuiGraphicsExtractor, screen: ContainerScreen, offX: Int, offY: Int) {
        val color = ChatFormatting.stripFormatting(screen.title.string)
            ?.let(STARTS_COLOR_REGEX::find)?.groupValues?.getOrNull(1)?.lowercase()?.trim() ?: return
        TerminalUtils.COLORS_SLOTS.forEach { slot ->
            val stack = screen.menu.slots[slot].item
            val name = ChatFormatting.stripFormatting(stack.hoverName.string)?.lowercase()?.trim() ?: return@forEach
            if (!stack.isEmpty && !TerminalUtils.isTerminalItemDone(stack) && TerminalUtils.normalizeItemName(name).startsWith(color)) {
                drawCell(ctx, TerminalType.COLORS, slot, cSolution, offX, offY)
            }
        }
    }

    private fun drawStartsWith(ctx: GuiGraphicsExtractor, screen: ContainerScreen, offX: Int, offY: Int) {
        val letter = ChatFormatting.stripFormatting(screen.title.string)
            ?.let(STARTS_WITH_REGEX::find)?.groupValues?.getOrNull(1)?.lowercase() ?: return
        TerminalUtils.STARTS_WITH_SLOTS.forEach { slot ->
            val stack = screen.menu.slots[slot].item
            val name = ChatFormatting.stripFormatting(stack.hoverName.string)?.trim()?.lowercase() ?: return@forEach
            if (!stack.isEmpty && !TerminalUtils.isTerminalItemDone(stack) && name.startsWith(letter)) {
                drawCell(ctx, TerminalType.STARTS_WITH, slot, cSolution, offX, offY)
            }
        }
    }

    private fun drawRedGreen(ctx: GuiGraphicsExtractor, screen: ContainerScreen, offX: Int, offY: Int) {
        TerminalUtils.RED_GREEN_SLOTS.forEach { slot ->
            if (screen.menu.slots[slot].item.item == Items.STAINED_GLASS_PANE.red()) drawCell(ctx, TerminalType.RED_GREEN, slot, cSolution, offX, offY)
        }
    }

    private fun drawRubix(ctx: GuiGraphicsExtractor, screen: ContainerScreen, offX: Int, offY: Int) {
        val solution = RubixTerminal.getFullSolution(screen) ?: return
        TerminalUtils.RUBIX_SLOTS.forEachIndexed { index, slot ->
            val clicks = solution[index]
            if (clicks == 0) return@forEachIndexed
            val position = TerminalOverlayLayout.slotToCompact(TerminalType.RUBIX, slot) ?: return@forEachIndexed
            val (x, y) = compactPosition(position.first, position.second, offX, offY)
            ctx.fill(x, y, x + CELL, y + CELL, if (clicks > 0) cRubixL else cRubixR)
            val text = clicks.toString()
            val width = mc.font.width(text)
            ctx.text(mc.font, text, x + (CELL - width) / 2, y + (CELL - mc.font.lineHeight) / 2, cTxtMain, true)
        }
    }

    private fun drawMelody(ctx: GuiGraphicsExtractor, screen: ContainerScreen, offX: Int, offY: Int, gridRows: Int) {
        val targetCol = (MELODY_LANE_START..MELODY_LANE_END).firstOrNull { screen.menu.slots[it].item.item == Items.STAINED_GLASS_PANE.magenta() } ?: return
        val targetCompact = targetCol - MELODY_LANE_START
        val containerSlots = screen.menu.slots.size - PLAYER_INV_SLOTS
        var correctRow = -1
        var paneSlot = -1
        for (slot in CHEST_ROW_WIDTH until containerSlots) {
            if (screen.menu.slots[slot].item.item != Items.STAINED_GLASS_PANE.lime()) continue
            if (slot % CHEST_ROW_WIDTH == targetCol) correctRow = slot / CHEST_ROW_WIDTH
            paneSlot = slot
        }
        val (indicatorX, indicatorY) = compactPosition(targetCompact, 0, offX, offY)
        ctx.fill(indicatorX, indicatorY, indicatorX + CELL, indicatorY + CELL, cMelodyIndicator)
        val (highlightX, highlightY) = compactPosition(targetCompact, MELODY_ROW_START, offX, offY)
        val bottomY = offY + gridRows * STRIDE - 2
        ctx.fill(highlightX, highlightY, highlightX + CELL, bottomY, cMelodyCol)
        (MELODY_ROW_START..MELODY_ROW_END).forEach { row ->
            val (x, y) = compactPosition(MELODY_BTN_COMPACT_COL, row, offX, offY)
            ctx.fill(x, y, x + CELL, y + CELL, if (row == correctRow) cMelodyBtnOn else cMelodyBtnOff)
        }
        val paneCol = paneSlot.takeIf { it >= 0 }?.rem(CHEST_ROW_WIDTH)?.minus(MELODY_LANE_START) ?: return
        val paneRow = paneSlot / CHEST_ROW_WIDTH
        if (paneCol in 0..(MELODY_LANE_END - MELODY_LANE_START) && paneRow in MELODY_ROW_START..MELODY_ROW_END) {
            val (x, y) = compactPosition(paneCol, paneRow, offX, offY)
            ctx.fill(x, y, x + CELL, y + CELL, cMelodyPane)
        }
    }

    private fun drawCell(ctx: GuiGraphicsExtractor, type: TerminalType, slot: Int, color: Int, offX: Int, offY: Int) {
        val position = TerminalOverlayLayout.slotToCompact(type, slot) ?: return
        val (x, y) = compactPosition(position.first, position.second, offX, offY)
        ctx.fill(x, y, x + CELL, y + CELL, color)
    }

    private fun compactPosition(cx: Int, cy: Int, offX: Int, offY: Int): Pair<Int, Int> =
        (cx * STRIDE + offX) to (cy * STRIDE + offY)

    private val STARTS_COLOR_REGEX = Regex("Select all the ([\\w ]+) items!")
    private val STARTS_WITH_REGEX = Regex("What starts with: \\W(\\w)\\W")
}
