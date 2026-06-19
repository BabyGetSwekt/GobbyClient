package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.events.gui.ScreenRenderEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonListener
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonClass
import gobby.utils.skyblock.dungeon.DungeonUtils.DungeonTeammate
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.ItemStack
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.network.HashedStack
import net.minecraft.ChatFormatting
import java.awt.Color

object LeapOverlay : Module("Spirit Leap Overlay", "Overlay to leap to classes easier", Category.DUNGEONS,) {

    val scale by NumberSetting("Scale", 100, 50, 200, desc = "Scale of the overlay UI (percent)")

    private data class LeapButton(
        val targetName: String,
        val teammate: DungeonTeammate,
        val headStack: ItemStack,
        var x: Int = 0,
        var y: Int = 0,
        var width: Int = 0,
        var height: Int = 0
    )

    private var buttons = listOf<LeapButton>()
    private var isActive = false
    private var hoveredButton: LeapButton? = null
    private var cachedSyncId = -1

    private const val CARD_WIDTH = 160
    private const val CARD_HEIGHT = 55
    private const val GRID_GAP = 10
    private const val COLS = 2
    private const val HEAD_SCALE = 2f
    private const val HEAD_RENDER_SIZE = 32 // 16 * HEAD_SCALE (2)

    private val CLASS_COLORS = mapOf(
        DungeonClass.Archer  to Color(255, 85, 85),
        DungeonClass.Berserk to Color(255, 170, 0),
        DungeonClass.Mage    to Color(85, 85, 255),
        DungeonClass.Tank    to Color(85, 255, 85),
        DungeonClass.Healer  to Color(170, 0, 170),
        DungeonClass.Unknown to Color(255, 255, 85)
    )

    private val CLASS_BG_COLORS = mapOf(
        DungeonClass.Archer  to Color(60, 15, 15, 200),
        DungeonClass.Berserk to Color(60, 35, 0, 200),
        DungeonClass.Mage    to Color(15, 15, 60, 200),
        DungeonClass.Tank    to Color(15, 50, 15, 200),
        DungeonClass.Healer  to Color(40, 0, 40, 200),
        DungeonClass.Unknown to Color(50, 50, 15, 200)
    )

    private val CLASS_HOVER_COLORS = mapOf(
        DungeonClass.Archer  to Color(80, 25, 25, 220),
        DungeonClass.Berserk to Color(80, 50, 10, 220),
        DungeonClass.Mage    to Color(25, 25, 80, 220),
        DungeonClass.Tank    to Color(25, 70, 25, 220),
        DungeonClass.Healer  to Color(60, 10, 60, 220),
        DungeonClass.Unknown to Color(70, 70, 25, 220)
    )

    private val CLASS_SORT_ORDER = mapOf(
        DungeonClass.Archer  to 0,
        DungeonClass.Berserk to 1,
        DungeonClass.Mage    to 2,
        DungeonClass.Tank    to 3,
        DungeonClass.Healer  to 4,
        DungeonClass.Unknown to 5
    )

    fun isOverlayActive(): Boolean {
        if (!enabled || !inDungeons) return false
        val screen = mc.gui.screen() as? ContainerScreen ?: return false
        return screen.title.string.contains("Spirit Leap")
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!inDungeons || !enabled) {
            if (isActive) deactivate()
            return
        }

        val screen = mc.gui.screen() as? ContainerScreen
        if (screen == null || !screen.title.string.contains("Spirit Leap")) {
            if (isActive) deactivate()
            return
        }

        cachedSyncId = screen.menu.containerId
        buttons = buildButtonsFromSlots(screen).sortedBy { CLASS_SORT_ORDER[it.teammate.dungeonClass] ?: 5 }
        isActive = buttons.isNotEmpty()
    }

    private fun buildButtonsFromSlots(screen: ContainerScreen): List<LeapButton> {
        val result = mutableListOf<LeapButton>()
        for (slotIndex in 10..18) {
            val slot = screen.menu.slots.getOrNull(slotIndex) ?: continue
            val stack = slot.item ?: continue
            if (stack.isEmpty) continue
            val itemName = ChatFormatting.stripFormatting(stack.hoverName.string)?.trim() ?: continue
            if (itemName.isBlank()) continue

            val teammate = DungeonListener.teammates[itemName]
                ?: DungeonTeammate(name = itemName, dungeonClass = DungeonClass.Unknown, classLevel = "?", playerLevel = 0)

            result.add(LeapButton(targetName = itemName, teammate = teammate, headStack = stack.copy()))
        }
        return result
    }

    private fun deactivate() {
        isActive = false
        buttons = emptyList()
        hoveredButton = null
        cachedSyncId = -1
    }

    @SubscribeEvent
    fun onScreenRender(event: ScreenRenderEvent) {
        if (!isActive) return
        val screen = event.screen as? ContainerScreen ?: return
        if (!screen.title.string.contains("Spirit Leap")) return

        val context = event.drawContext
        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight

        val uiScale = scale / 100f
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val scaledMouseX = ((event.mouseX - centerX) / uiScale + centerX).toInt()
        val scaledMouseY = ((event.mouseY - centerY) / uiScale + centerY).toInt()

        drawDarkOverlay(context, screenWidth, screenHeight)

        context.pose().pushMatrix()
        applyScaleAroundCenter(context, centerX, centerY, uiScale)
        drawGrid(context, screenWidth, screenHeight, scaledMouseX, scaledMouseY)
        context.pose().popMatrix()
    }

    fun handleClick(mouseX: Double, mouseY: Double): Boolean {
        if (!isActive) return false
        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight
        val uiScale = scale / 100f
        val centerX = screenWidth / 2f
        val centerY = screenHeight / 2f
        val scaledMouseX = ((mouseX - centerX) / uiScale + centerX).toInt()
        val scaledMouseY = ((mouseY - centerY) / uiScale + centerY).toInt()
        clickButton(scaledMouseX, scaledMouseY)
        return true
    }

    private fun drawDarkOverlay(context: GuiGraphicsExtractor, width: Int, height: Int) {
        context.fill(0, 0, width, height, Color(0, 0, 0, 160).rgb)
    }

    private fun applyScaleAroundCenter(context: GuiGraphicsExtractor, centerX: Float, centerY: Float, scale: Float) {
        context.pose().translate(centerX, centerY)
        context.pose().scale(scale, scale)
        context.pose().translate(-centerX, -centerY)
    }

    private fun drawGrid(context: GuiGraphicsExtractor, screenWidth: Int, screenHeight: Int, mouseX: Int, mouseY: Int) {
        val rows = (buttons.size + COLS - 1) / COLS
        val gridWidth = COLS * CARD_WIDTH + (COLS - 1) * GRID_GAP
        val gridHeight = rows * CARD_HEIGHT + (rows - 1) * GRID_GAP
        val gridStartX = (screenWidth - gridWidth) / 2
        val gridStartY = (screenHeight - gridHeight) / 2

        hoveredButton = null

        for ((index, button) in buttons.withIndex()) {
            val col = index % COLS
            val row = index / COLS
            val bx = gridStartX + col * (CARD_WIDTH + GRID_GAP)
            val by = gridStartY + row * (CARD_HEIGHT + GRID_GAP)

            button.x = bx
            button.y = by
            button.width = CARD_WIDTH
            button.height = CARD_HEIGHT

            val isHovered = mouseX in bx..(bx + CARD_WIDTH) && mouseY in by..(by + CARD_HEIGHT)
            if (isHovered) hoveredButton = button

            drawCard(context, button, bx, by, isHovered)
        }
    }

    private fun drawCard(context: GuiGraphicsExtractor, button: LeapButton, x: Int, y: Int, hovered: Boolean) {
        val dungeonClass = button.teammate.dungeonClass
        val bgColor = (if (hovered) CLASS_HOVER_COLORS else CLASS_BG_COLORS)[dungeonClass] ?: Color.DARK_GRAY
        val accentColor = CLASS_COLORS[dungeonClass] ?: Color.WHITE

        // Background + left accent
        context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bgColor.rgb)
        context.fill(x, y, x + 3, y + CARD_HEIGHT, accentColor.rgb)

        if (hovered) {
            context.fill(x, y, x + CARD_WIDTH, y + 1, accentColor.rgb)
            context.fill(x, y + CARD_HEIGHT - 1, x + CARD_WIDTH, y + CARD_HEIGHT, accentColor.rgb)
            context.fill(x + CARD_WIDTH - 1, y, x + CARD_WIDTH, y + CARD_HEIGHT, accentColor.rgb)
        }

        drawScaledHead(context, button.headStack, x + 8, y + (CARD_HEIGHT - HEAD_RENDER_SIZE) / 2)

        val textX = x + 46
        context.text(mc.font, button.teammate.name, textX, y + 14, accentColor.rgb, true)
        val classText = "${dungeonClass.name} ${button.teammate.classLevel}"
        context.text(mc.font, classText, textX, y + 28, Color(150, 150, 160).rgb, true)
    }

    private fun drawScaledHead(context: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        context.pose().pushMatrix()
        context.pose().translate(x.toFloat(), y.toFloat())
        context.pose().scale(HEAD_SCALE, HEAD_SCALE)
        context.item(stack, 0, 0)
        context.pose().popMatrix()
    }

    private fun clickButton(mouseX: Int, mouseY: Int) {
        val button = buttons.firstOrNull {
            it.width > 0 && it.height > 0 &&
                mouseX in it.x..(it.x + it.width) && mouseY in it.y..(it.y + it.height)
        } ?: return
        val screen = mc.gui.screen() as? ContainerScreen ?: return
        val handler = screen.menu
        val player = mc.player ?: return
        val connection = mc.connection ?: return

        val targetName = button.targetName
        var slotId = -1
        for (slot in handler.slots) {
            val stack = slot.item ?: continue
            if (stack.isEmpty) continue
            val itemName = ChatFormatting.stripFormatting(stack.hoverName.string)?.trim() ?: continue
            if (itemName.equals(targetName, ignoreCase = true)) {
                slotId = slot.index
                break
            }
        }
        if (slotId < 0) return

        val slots = handler.slots
        val before = slots.map { it.item.copy() }
        handler.clicked(slotId, 0, ContainerInput.CLONE, player)

        val changed = Int2ObjectOpenHashMap<HashedStack>()
        for (i in before.indices) {
            if (!ItemStack.matches(before[i], slots[i].item)) {
                changed.put(i, HashedStack.create(slots[i].item, connection.decoratedHashOpsGenenerator()))
            }
        }

        connection.send(
            ServerboundContainerClickPacket(
                handler.containerId,
                handler.stateId,
                slotId.toShort(),
                0.toByte(),
                ContainerInput.CLONE,
                changed,
                HashedStack.create(handler.carried, connection.decoratedHashOpsGenenerator())
            )
        )
    }
}
