package gobby.events.gui

import gobby.events.Events
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen

class ScreenRenderEvent(
    val screen: Screen,
    val drawContext: GuiGraphicsExtractor,
    val mouseX: Int,
    val mouseY: Int,
    val delta: Float
) : Events()
