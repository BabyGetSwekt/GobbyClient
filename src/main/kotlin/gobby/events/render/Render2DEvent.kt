package gobby.events.render

import gobby.events.Events
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.DeltaTracker

class Render2DEvent(
    val matrices: GuiGraphicsExtractor,
    val renderTickCounter: DeltaTracker
): Events()