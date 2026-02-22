package gobby.features.dungeons

import gobby.config.GobbyConfig
import gobby.sounds.SoundManager
import gobby.events.RunFinishedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.GuiElement
import gobby.gui.GuiElementManager
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier

object YouAreAKingGG : GuiElement() {

    private val TEXTURE = Identifier.of("gobbyclient", "textures/gui/victory_royale.png")
    private const val ASPECT_RATIO = 995f / 246f

    init {
        GuiElementManager.register(this)
    }

    @SubscribeEvent
    fun onRunEnd(event: RunFinishedEvent) {
        if (!GobbyConfig.fortniteMode) return
        show(durationMs = 5000L, fadeInMs = 500L, fadeOutMs = 1000L)
        SoundManager.play("assets/gobbyclient/sounds/victory_royale_sound.ogg")
    }

    override fun render(drawContext: DrawContext, screenWidth: Int, screenHeight: Int, alpha: Float) {
        val width = (screenWidth * 0.6f).toInt()
        val height = (width / ASPECT_RATIO).toInt()
        val x = (screenWidth - width) / 2
        val y = (screenHeight * 0.08f).toInt()

        drawTexture(drawContext, TEXTURE, x, y, width, height, alpha)
    }
}
