package gobby.utils

import gobby.events.ClientTickEvent
import gobby.events.PacketSentEvent
import gobby.events.core.SubscribeEvent
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

object MovementPacketSuppressor {

    private var suppressNext = false

    fun suppressNext() {
        suppressNext = true
    }

    fun clear() {
        suppressNext = false
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        if (!suppressNext) return
        if (event.packet !is ServerboundMovePlayerPacket) return
        suppressNext = false
        event.cancel()
    }

    @SubscribeEvent
    fun onTickPost(event: ClientTickEvent.Post) = clear()
}
