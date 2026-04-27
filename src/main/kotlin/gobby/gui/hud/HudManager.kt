package gobby.gui.hud

import gobby.Gobbyclient.Companion.mc
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render2DEvent

object HudManager {

    private val huds = mutableListOf<HudSetting>()

    fun register(hud: HudSetting) {
        huds.add(hud)
    }

    fun getAll(): List<HudSetting> = huds

    @SubscribeEvent
    fun onRender2D(event: Render2DEvent) {
        if (mc.currentScreen is HudEditor) return
        val ctx = event.matrices
        for (hud in huds) {
            hud.renderHud(ctx, false)
        }
    }
}
