package gobby.events.core

import net.minecraft.network.chat.Component
import java.util.Collections
import java.util.IdentityHashMap

private const val MAX_PENDING = 32

object ChatSuppressor {

    private val hidden: MutableSet<Component> =
        Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    fun hide(message: Component) {
        if (hidden.size >= MAX_PENDING) hidden.clear()
        hidden += message
    }

    fun consume(message: Component): Boolean = hidden.remove(message)
}
