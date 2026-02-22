package gobby.events.core

import gobby.Gobbyclient
import gobby.events.ChatReceivedEvent
import gobby.events.PacketReceivedEvent
import gobby.events.RunFinishedEvent
import gobby.events.ServerTickEvent
import gobby.utils.LocationUtils
import gobby.utils.skyblock.dungeon.DungeonListener.endDialogues
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket

object EventDispatcher {

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (event.packet is CommonPingS2CPacket) Gobbyclient.EVENT_MANAGER.publish(ServerTickEvent())
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        val floor = LocationUtils.dungeonFloor
        if (floor == -1 ) return
        val dialogues = endDialogues[floor] ?: return
        if (event.message in dialogues) Gobbyclient.EVENT_MANAGER.publish(RunFinishedEvent())
    }
}