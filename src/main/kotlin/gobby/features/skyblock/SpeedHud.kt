package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.hud.HudSetting
import java.awt.Color
import kotlin.math.roundToInt

object SpeedHud : Module("Speed HUD", "Displays your current Skyblock ✦ Speed", Category.SKYBLOCK) {

    private val speedHud by HudSetting("Speed", "Shows current Skyblock walk speed") { example ->
        val ctx = drawContext ?: return@HudSetting
        val speed = if (example) 400 else currentSkyblockSpeed()
        val text = "§f✦ Speed §a$speed"
        val tr = mc.textRenderer
        ctx.drawText(tr, text, 0, 0, Color.WHITE.rgb, true)
        setSize(tr.getWidth(text), tr.fontHeight)
    }

    private fun currentSkyblockSpeed(): Int {
        val walkSpeed = mc.player?.abilities?.walkSpeed ?: 0.1f
        return (walkSpeed * 1000f).roundToInt()
    }
}
