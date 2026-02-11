package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.modMessage
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents

object DevTest {

    fun init() {

        if (mc.player == null || mc.world == null) return
        ScreenKeyboardEvents.allowKeyPress(mc?.currentScreen).register { _, keyInput ->
            modMessage("Key pressed: key=${keyInput.key()} scancode=${keyInput.scancode()} modifiers=${keyInput.modifiers()}") // works

            if(keyInput.key() == 71) { // Just testing with G atm
                modMessage("Character intercepted: g")
                false // Currently have set as false here, but how do I cancel?
            } else {
                true
            }
        }
    }
}