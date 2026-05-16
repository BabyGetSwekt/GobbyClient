package gobby.events.render

import gobby.events.Events
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.DeltaTracker

class Render2DEvent(
    val matrices: GuiGraphics,
    val renderTickCounter: DeltaTracker
): Events()