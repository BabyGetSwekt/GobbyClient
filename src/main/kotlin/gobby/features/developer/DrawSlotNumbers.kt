package gobby.features.developer

import gobby.gui.click.Category
import gobby.gui.click.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component

object DrawSlotNumbers : Module("Draw Slot Numbers", "Draws slot index numbers in container GUIs", Category.DEVELOPER) {

    fun onDrawSlots(screen: AbstractContainerScreen<*>, ctx: GuiGraphics) {
        if (!enabled) return
        val handler = screen.menu

        for (slot in handler.slots) {
            ctx.drawString(Minecraft.getInstance().font, Component.literal(slot.index.toString()), slot.x, slot.y, 0xFFFFFFFF.toInt(), true)
        }
    }
}
