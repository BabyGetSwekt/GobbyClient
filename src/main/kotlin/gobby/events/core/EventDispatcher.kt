package gobby.events.core

import gobby.Gobbyclient
import gobby.events.ChatReceivedEvent
import gobby.events.PacketReceivedEvent
import gobby.events.RunFinishedEvent
import gobby.events.ServerTickEvent
import gobby.events.network.SystemChatReceivedEvent
import gobby.utils.ChatUtils.noControlCodes
import gobby.utils.LocationUtils
import gobby.utils.skyblock.dungeon.DungeonListener.endDialogues
import net.minecraft.network.protocol.common.ClientboundPingPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket

object EventDispatcher {

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        when (val p = event.packet) {
            is ClientboundPingPacket -> Gobbyclient.EVENT_MANAGER.publish(ServerTickEvent())
            is ClientboundSystemChatPacket -> {
                if (Gobbyclient.EVENT_MANAGER.publish(SystemChatReceivedEvent(p.content().string.noControlCodes, p.content(), p.overlay())).isCanceled) event.cancel()
            }
        }
    }

    @SubscribeEvent
    fun onChat(event: ChatReceivedEvent) {
        val floor = LocationUtils.dungeonFloor
        if (floor == -1 ) return
        val dialogues = endDialogues[floor] ?: return
        if (event.message in dialogues) Gobbyclient.EVENT_MANAGER.publish(RunFinishedEvent())
    }
}