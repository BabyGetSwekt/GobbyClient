package gobby.gui.hud

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.FONT_STYLE
import gobby.gui.click.HudButton
import gobby.gui.click.Module
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
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
    var drawContext: GuiGraphicsExtractor? = null
        private set

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): HudSetting {
        module = thisRef
        HudManager.register(this)
        thisRef.settings.add(HudButton(name, desc) {
            //? if >26.1.2
            mc.execute { mc.gui.setScreen(HudEditor(thisRef)) }
            //? if <=26.1.2
            /*mc.execute { mc.setScreen(HudEditor(thisRef)) }*/
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

    fun renderHud(ctx: GuiGraphicsExtractor, example: Boolean) {
        val mod = module ?: return
        if (!example && (!mod.enabled || !visible())) return

        drawContext = ctx
        lastWidth = 0
        lastHeight = 0

        ctx.pose().pushMatrix()
        ctx.pose().translate(hudX, hudY)
        ctx.pose().scale(hudScale, hudScale)
        render(example)
        ctx.pose().popMatrix()

        drawContext = null
    }

    fun styledFont(text: String, color: Color = Color.WHITE) {
        val ctx = drawContext ?: return
        val tr = mc.font
        val styled = styledColored(text, color)
        val width = tr.width(styled)
        ctx.text(tr, styled, 0, lastHeight, -1, true)
        lastWidth = maxOf(lastWidth, width)
        lastHeight += tr.lineHeight
    }

    fun styledText(text: Component) {
        val ctx = drawContext ?: return
        val tr = mc.font
        val width = tr.width(text)
        ctx.text(tr, text, 0, lastHeight, -1, true)
        lastWidth = maxOf(lastWidth, width)
        lastHeight += tr.lineHeight
    }

    private fun styledColored(s: String, color: Color): Component {
        val argb = (0xFF shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
        return Component.literal(s).setStyle(FONT_STYLE.withColor(argb))
    }
}
