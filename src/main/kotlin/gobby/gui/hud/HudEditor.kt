package gobby.gui.hud

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.ConfigManager
import gobby.gui.click.Module
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import java.awt.Color

class HudEditor(private val filterModule: Module? = null) : Screen(Text.literal("HUD Editor")) {

    private var dragging: HudSetting? = null
    private var dragOffX = 0f
    private var dragOffY = 0f

    private fun visibleHuds(): List<HudSetting> {
        return if (filterModule != null) {
            HudManager.getAll().filter { it.module == filterModule }
        } else {
            HudManager.getAll().filter { it.module?.enabled == true }
        }
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        for (hud in visibleHuds()) {
            hud.renderHud(context, true)

            val x = hud.hudX.toInt()
            val y = hud.hudY.toInt()
            val w = (hud.getWidth() * hud.hudScale).toInt()
            val h = (hud.getHeight() * hud.hudScale).toInt()

            val borderColor = if (dragging == hud) Color(80, 220, 100, 180).rgb else Color(150, 150, 160, 120).rgb
            context.fill(x - 1, y - 1, x + w + 1, y, borderColor)
            context.fill(x - 1, y + h, x + w + 1, y + h + 1, borderColor)
            context.fill(x - 1, y, x, y + h, borderColor)
            context.fill(x + w, y, x + w + 1, y + h, borderColor)

            context.drawText(mc.textRenderer, hud.name, x, y - 10, Color(200, 200, 210).rgb, true)
        }
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        val mx = click.x()
        val my = click.y()

        for (hud in visibleHuds()) {
            val x = hud.hudX
            val y = hud.hudY
            val w = hud.getWidth() * hud.hudScale
            val h = hud.getHeight() * hud.hudScale

            if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
                dragging = hud
                dragOffX = (mx - x).toFloat()
                dragOffY = (my - y).toFloat()
                return true
            }
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: Click, offsetX: Double, offsetY: Double): Boolean {
        val hud = dragging ?: return super.mouseDragged(click, offsetX, offsetY)
        hud.hudX = (click.x() + offsetX - dragOffX).toFloat()
        hud.hudY = (click.y() + offsetY - dragOffY).toFloat()
        return true
    }

    override fun mouseReleased(click: Click): Boolean {
        if (dragging != null) {
            dragging = null
            ConfigManager.save()
            return true
        }
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        for (hud in visibleHuds()) {
            val x = hud.hudX
            val y = hud.hudY
            val w = hud.getWidth() * hud.hudScale
            val h = hud.getHeight() * hud.hudScale

            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                hud.hudScale = (hud.hudScale + verticalAmount.toFloat() * 0.1f).coerceIn(0.5f, 4f)
                ConfigManager.save()
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun close() {
        ConfigManager.save()
        super.close()
    }

    override fun shouldPause(): Boolean = false
}
