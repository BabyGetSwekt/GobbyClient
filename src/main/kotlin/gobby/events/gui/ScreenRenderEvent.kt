package gobby.events.gui

import gobby.events.Events
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen

class ScreenRenderEvent(
    val screen: Screen,
    val drawContext: DrawContext,
    val mouseX: Int,
    val mouseY: Int,
    val delta: Float
) : Events()
