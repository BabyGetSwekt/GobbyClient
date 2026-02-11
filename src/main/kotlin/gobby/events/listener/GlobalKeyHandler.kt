package gobby.events.listener

import gobby.events.core.SubscribeEvent
import gobby.events.KeyPressGuiEvent
import gobby.events.CharTypedEvent
import gobby.utils.ChatUtils.modMessage

object GlobalKeyHandler {
    private val listeners = mutableListOf<(Any) -> Boolean>()

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent): Boolean {
        var cancelled = false
        for (listener in listeners) {
            if (listener(event)) {
                cancelled = true
                break
            }
        }
        if (cancelled) {
            event.cancel()
        }
        return cancelled
    }

    @SubscribeEvent
    fun onCharTyped(event: CharTypedEvent): Boolean {
        var cancelled = false
        for (listener in listeners) {
            if (listener(event)) {
                cancelled = true
                break
            }
        }
        if (cancelled) {
            event.cancel()
        }
        return cancelled
    }

    fun register(listener: (Any) -> Boolean) {
        listeners.add(listener)
    }

    fun unregister(listener: (Any) -> Boolean) {
        listeners.remove(listener)
    }

    fun allRegisters() {
        modMessage("All the current listeners are ")
    }
}