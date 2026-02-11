package gobby.utils

import gobby.Gobbyclient.Companion.mc
import gobby.mixinterface.IClientConnectionAccessor
import gobby.utils.ChatUtils.modMessage

object PacketUtils {

    fun getSequence(): Int {
        val connection = mc.networkHandler?.connection
        val interactSequence = if (connection is IClientConnectionAccessor) {
            connection.getInteractSequence()
        } else {
            modMessage("Failed to get interact sequence, this is probably a bug.")
            0
        }
        return interactSequence
    }
}