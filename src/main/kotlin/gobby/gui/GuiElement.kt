package gobby.gui

import gobby.Gobbyclient.Companion.mc
import gobby.events.render.Render2DEvent
import gobby.utils.render.Render2D
import gobby.utils.timer.Clock
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import java.awt.Color

abstract class GuiElement {

    private val clock = Clock()
    private var duration = 0L
    private var fadeIn = 0L
    private var fadeOut = 0L
    var active = false
        private set

    fun show(durationMs: Long, fadeInMs: Long = 0L, fadeOutMs: Long = 0L) {
        this.duration = durationMs
        this.fadeIn = fadeInMs
        this.fadeOut = fadeOutMs
        clock.update()
        active = true
    }

    open fun hide() {
        active = false
    }

    protected fun getAlpha(): Float {
        val elapsed = clock.getTime()
        val totalTime = fadeIn + duration + fadeOut

        if (elapsed >= totalTime) {
            hide()
            return 0f
        }

        if (elapsed < fadeIn && fadeIn > 0) {
            return elapsed.toFloat() / fadeIn
        }

        if (elapsed > fadeIn + duration && fadeOut > 0) {
            val fadeOutElapsed = elapsed - fadeIn - duration
            return 1f - (fadeOutElapsed.toFloat() / fadeOut)
        }

        return 1f
    }

    abstract fun render(drawContext: DrawContext, screenWidth: Int, screenHeight: Int, alpha: Float)

    fun onRender2D(event: Render2DEvent) {
        if (!active) return
        val alpha = getAlpha()
        if (alpha <= 0f) return
        render(event.matrices, mc.window.scaledWidth, mc.window.scaledHeight, alpha)
    }

    protected fun drawTexture(
        drawContext: DrawContext,
        texture: Identifier,
        x: Int, y: Int,
        width: Int, height: Int,
        alpha: Float
    ) {
        if (texture !in registeredTextures) {
            val path = "assets/${texture.namespace}/${texture.path}"
            val stream = GuiElement::class.java.classLoader.getResourceAsStream(path)
            if (stream != null) {
                val nativeImage = NativeImage.read(stream)
                mc.textureManager.registerTexture(texture, NativeImageBackedTexture({ texture.toString() }, nativeImage))
                stream.close()
            }
            registeredTextures.add(texture)
        }
        val argb = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        drawContext.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            texture,
            x, y,
            0f, 0f,
            width, height,
            width, height,
            argb
        )
    }

    companion object {
        private val registeredTextures = mutableSetOf<Identifier>()
    }

    protected fun drawCenteredText(
        drawContext: DrawContext,
        text: String,
        centerX: Float, y: Float,
        color: Color,
        scale: Float,
        alpha: Float
    ) {
        val textWidth = mc.textRenderer.getWidth(text) * scale
        val x = centerX - (textWidth / 2f)
        val renderColor = Color(color.red, color.green, color.blue, (alpha * 255).toInt().coerceIn(0, 255))
        Render2D.drawString(text, x, y, renderColor, scale, drawContext)
    }
}
