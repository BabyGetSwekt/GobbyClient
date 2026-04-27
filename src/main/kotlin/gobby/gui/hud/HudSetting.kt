package gobby.gui.hud

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.ClickGUITheme
import gobby.gui.click.HudButton
import gobby.gui.click.Module
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import java.awt.Color
import kotlin.reflect.KProperty

class HudSetting(
    val name: String,
    val desc: String = "",
    private val visible: () -> Boolean = { true },
    private val render: HudSetting.(example: Boolean) -> Unit
) {

    var hudX = 0f
    var hudY = 0f
    var hudScale = 1f
    var module: Module? = null

    private var lastWidth = 0
    private var lastHeight = 0
    var drawContext: DrawContext? = null
        private set

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): HudSetting {
        module = thisRef
        HudManager.register(this)
        thisRef.settings.add(HudButton(name, desc) {
            mc.send { mc.setScreen(HudEditor(thisRef)) }
        })
        return this
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): HudSetting = this

    fun getWidth(): Int = lastWidth
    fun getHeight(): Int = lastHeight

    fun setSize(width: Int, height: Int) {
        lastWidth = maxOf(lastWidth, width)
        lastHeight = maxOf(lastHeight, height)
    }

    fun renderHud(ctx: DrawContext, example: Boolean) {
        val mod = module ?: return
        if (!example && (!mod.enabled || !visible())) return

        drawContext = ctx
        lastWidth = 0
        lastHeight = 0

        ctx.matrices.pushMatrix()
        ctx.matrices.translate(hudX, hudY)
        ctx.matrices.scale(hudScale, hudScale)
        render(example)
        ctx.matrices.popMatrix()

        drawContext = null
    }

    fun styledFont(text: String, color: Color = Color.WHITE) {
        val ctx = drawContext ?: return
        val tr = mc.textRenderer
        val styled = styledColored(text, color)
        val width = tr.getWidth(styled)
        ctx.drawText(tr, styled, 0, lastHeight, -1, true)
        lastWidth = maxOf(lastWidth, width)
        lastHeight += tr.fontHeight
    }

    fun styledText(text: Text) {
        val ctx = drawContext ?: return
        val tr = mc.textRenderer
        val width = tr.getWidth(text)
        ctx.drawText(tr, text, 0, lastHeight, -1, true)
        lastWidth = maxOf(lastWidth, width)
        lastHeight += tr.fontHeight
    }

    private fun styledColored(s: String, color: Color): Text {
        val argb = (0xFF shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
        return Text.literal(s).setStyle(ClickGUITheme.FONT_STYLE.withColor(argb))
    }
}
