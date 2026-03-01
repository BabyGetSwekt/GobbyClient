package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.ChatUtils.modMessage

object KeybindListener {

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (mc.currentScreen != null) return

        val key = event.key
        if (key == 0) return

        for (module in Module.modules) {
            val kb = module.keybindSetting ?: continue
            if (kb.value == key && module.toggled && !module.isAlwaysEnabled) {
                module.enabled = !module.enabled
                val state = if (module.enabled) "§aenabled" else "§cdisabled"
                modMessage("${module.name} $state")
                ConfigManager.save()
            }
        }
    }
}
