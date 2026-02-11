package gobby.gui.components

import gg.essential.elementa.components.UIBlock
import gg.essential.elementa.components.UIText
import gg.essential.elementa.constraints.CenterConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.elementa.dsl.toConstraint
import gg.essential.universal.USound
import gg.essential.vigilance.gui.DataBackedSetting
import gg.essential.vigilance.gui.settings.SettingComponent
import gobby.Gobbyclient
import gobby.events.KeyPressGuiEvent
import gobby.events.CharTypedEvent
import gobby.events.listener.GlobalKeyHandler
import org.lwjgl.glfw.GLFW
import java.awt.Color

class KeybindPropertyComponent(initialValue: Int) : SettingComponent() {

    private var listeningForKey = false
    private var currentKey: Int = initialValue
    private val keyDisplay: UIText
    private val container: UIBlock

    init {
        container = UIBlock().constrain {
            x = (DataBackedSetting.INNER_PADDING + 10f).pixels(alignOpposite = true)
            y = CenterConstraint()
            width = 50.pixels()
            height = 25.pixels()
            color = Color(30, 30, 30, 180).toConstraint()
        }.onMouseEnter {
            USound.playButtonPress()
            startListeningForKey()
        }.onMouseLeave {
            stopListeningForKey()
        } as UIBlock childOf this

        keyDisplay = UIText(getKeyName(currentKey)).constrain {
            x = CenterConstraint()
            y = CenterConstraint()
        } childOf container

        // Register once
        GlobalKeyHandler.register { event ->
            when (event) {
                is KeyPressGuiEvent -> handleKeyPress(event)
                is CharTypedEvent -> handleCharTyped(event)
                else -> false
            }
        }
    }

    private fun startListeningForKey() {
        Gobbyclient.logger.info("Listening for key press...")
        listeningForKey = true
        keyDisplay.setText("Press a key...")
    }

    private fun stopListeningForKey() {
        listeningForKey = false
        keyDisplay.setText(getKeyName(currentKey))
    }

    private fun handleKeyPress(event: KeyPressGuiEvent): Boolean {
        if (listeningForKey) {
            event.cancel()
            setKeybind(event.key)
            stopListeningForKey()
            return true
        }
        return false
    }

    private fun handleCharTyped(event: CharTypedEvent): Boolean {
        if (listeningForKey) {
            event.cancel()
            setKeybind(event.key)
            stopListeningForKey()
            return true
        }
        return false
    }

    private fun setKeybind(keyCode: Int) {
        currentKey = keyCode
        keyDisplay.setText(getKeyName(keyCode))
        changeValue(currentKey)
    }

    private fun getKeyName(keyCode: Int): String {
        val name = GLFW.glfwGetKeyName(keyCode, 0)
        return name ?: if (keyCode in 32..126) keyCode.toChar().toString() else "Unknown"
    }
}
