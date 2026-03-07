package gobby.features.developer

import gobby.gui.click.Category
import gobby.gui.click.Module
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.text.Text

object DrawSlotNumbers : Module("Draw Slot Numbers", "Draws slot index numbers in container GUIs", Category.DEVELOPER) {

    fun onDrawSlots(screen: HandledScreen<*>, ctx: DrawContext) {
        if (!enabled) return
        val handler = screen.screenHandler

        for (slot in handler.slots) {
            ctx.drawText(screen.textRenderer, Text.literal(slot.id.toString()), slot.x, slot.y, 0xFFFFFFFF.toInt(), true)
        }
    }
}
