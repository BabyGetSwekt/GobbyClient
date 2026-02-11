package gobby.events.render

import gobby.events.Events
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter

class Render2DEvent(
    val matrices: DrawContext,
    val renderTickCounter: RenderTickCounter
): Events()