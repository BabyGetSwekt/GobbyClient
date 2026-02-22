package gobby.gui

import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render2DEvent

object GuiElementManager {

    private val elements = mutableListOf<GuiElement>()

    fun register(element: GuiElement) {
        elements.add(element)
    }

    @SubscribeEvent
    fun onRender2D(event: Render2DEvent) {
        for (element in elements) {
            element.onRender2D(event)
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        for (element in elements) {
            element.hide()
        }
    }
}
