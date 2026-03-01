package gobby.features.dungeons

import gobby.events.RunFinishedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.GuiElement
import gobby.gui.GuiElementManager
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.sounds.SoundManager
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.Identifier

object YouAreAKingGG : GuiElement() {

    private val mod = Module("You Are A King GG", "Fortnite.", Category.DUNGEONS)

    private val TEXTURE = Identifier.of("gobbyclient", "textures/gui/victory_royale.png")
    private const val ASPECT_RATIO = 995f / 246f

    init {
        GuiElementManager.register(this)
    }

    @SubscribeEvent
    fun onRunEnd(event: RunFinishedEvent) {
        if (!mod.enabled) return
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
