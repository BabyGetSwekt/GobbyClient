package gobby.features.render

import gobby.Gobbyclient.Companion.mc
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.components.hud.KeystrokesHud
import gobby.gui.hud.HudSetting

object Keystrokes : Module("Keystrokes", "Shows your movement keys on screen", Category.RENDER) {

    private val keystrokesHud by HudSetting("Keystrokes", "Movable key display") { example ->
        val ctx = drawContext ?: return@HudSetting
        val options = mc.options
        val bindings = KeystrokesHud.KeyBindings(
            forward = options.keyUp,
            left = options.keyLeft,
            back = options.keyDown,
            right = options.keyRight
        )
        val size = KeystrokesHud.renderKeystrokes(ctx, bindings, example)
        setSize(size.width, size.height)
    }
}
