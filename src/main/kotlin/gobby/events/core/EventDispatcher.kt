package gobby.events.core

import gobby.Gobbyclient
import gobby.events.PacketReceivedEvent
import gobby.events.ServerTickEvent
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket

object EventDispatcher {

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (event.packet is CommonPingS2CPacket) Gobbyclient.EVENT_MANAGER.publish(ServerTickEvent())
    }
}