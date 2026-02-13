package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.config.GobbyConfig
import gobby.events.PacketReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.Utils.equalsOneOf
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket

object AutoCloseChest {

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (mc.player == null || mc.world == null) return
        val packet = event.packet as? OpenScreenS2CPacket ?: return
        if (!inDungeons || inBoss || !GobbyConfig.autoCloseChest ) return
        if (!packet.name.string.equalsOneOf("Chest", "Large Chest")) return

        mc.networkHandler?.sendPacket(CloseHandledScreenC2SPacket(packet.syncId))
        event.cancel()
    }
}
