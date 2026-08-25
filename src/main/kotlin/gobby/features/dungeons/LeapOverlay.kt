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
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.world.item.ItemStack
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.network.HashedStack
import net.minecraft.ChatFormatting

object LeapOverlay : Module("Spirit Leap Overlay", "Overlay to leap to classes easier", Category.DUNGEONS,) {

    val scale by NumberSetting("Scale", 100, 50, 200, desc = "Scale of the overlay UI (percent)")

    private var buttons = listOf<LeapOverlayButton>()
    private var isActive = false
    private var hoveredButton: LeapOverlayButton? = null
    private var cachedSyncId = -1

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

    private fun buildButtonsFromSlots(screen: ContainerScreen): List<LeapOverlayButton> {
        val result = mutableListOf<LeapOverlayButton>()
        for (slotIndex in 10..18) {
            val slot = screen.menu.slots.getOrNull(slotIndex) ?: continue
            val stack = slot.item ?: continue
            if (stack.isEmpty) continue
            val itemName = ChatFormatting.stripFormatting(stack.hoverName.string)?.trim() ?: continue
            if (itemName.isBlank()) continue

            val teammate = DungeonListener.teammates[itemName]
                ?: DungeonTeammate(name = itemName, dungeonClass = DungeonClass.Unknown, classLevel = "?", playerLevel = 0)

            result.add(LeapOverlayButton(targetName = itemName, teammate = teammate, headStack = stack.copy()))
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

        context.fill(0, 0, screenWidth, screenHeight, java.awt.Color(0, 0, 0, 160).rgb)

        context.pose().pushMatrix()
        applyScaleAroundCenter(context, centerX, centerY, uiScale)
        hoveredButton = LeapOverlayRenderer.draw(context, screenWidth, screenHeight, scaledMouseX, scaledMouseY, buttons)
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

    private fun applyScaleAroundCenter(context: net.minecraft.client.gui.GuiGraphicsExtractor, centerX: Float, centerY: Float, scale: Float) {
        context.pose().translate(centerX, centerY)
        context.pose().scale(scale, scale)
        context.pose().translate(-centerX, -centerY)
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

        val slotId = findSlotId(handler, button.targetName)
        if (slotId < 0) return
        sendContainerClick(handler, player, connection, slotId)
    }

    private fun findSlotId(handler: net.minecraft.world.inventory.AbstractContainerMenu, targetName: String): Int =
        handler.slots.firstOrNull { slot ->
            val stack = slot.item
            !stack.isEmpty && ChatFormatting.stripFormatting(stack.hoverName.string)?.trim()?.equals(targetName, true) == true
        }?.index ?: -1

    private fun sendContainerClick(handler: net.minecraft.world.inventory.AbstractContainerMenu, player: net.minecraft.world.entity.player.Player, connection: net.minecraft.client.multiplayer.ClientPacketListener, slotId: Int) {
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
