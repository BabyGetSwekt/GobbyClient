package gobby.features.galatea

import gobby.utils.ChatUtils.modMessage
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW

object KeybindHandler {
    private val GOBBY_CATEGORY = KeyBinding.Category.create(Identifier.of("gobby", "main"))
    private lateinit var toggleKey: KeyBinding

    fun register() {
        // Register a custom keybinding for G
        toggleKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.gobby.toggle", // translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G, // The G key
                GOBBY_CATEGORY  // keybinding category
            )
        )

        // Listen for client ticks
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (toggleKey.wasPressed()) {
                modMessage("Toggle key pressed!")
            }
        }
    }
}
