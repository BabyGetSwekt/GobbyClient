package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.saveddata.maps.MapId
import net.minecraft.world.level.saveddata.maps.MapItemSavedData

object DungeonMapSource {

    private const val MAP_PIXELS = 128 * 128
    private const val EMPTY_CORNER: Byte = 0

    @Volatile
    private var mapId: MapId? = null

    val savedData: MapItemSavedData?
        get() {
            val id = mapId ?: return null
            val level = mc.level ?: return null
            return MapItem.getSavedData(id, level)?.takeIf { it.isDungeonMap }
        }

    private val MapItemSavedData.isDungeonMap: Boolean
        get() = colors.size >= MAP_PIXELS && colors[0] == EMPTY_CORNER

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        val packet = event.packet as? ClientboundMapItemDataPacket ?: return
        mapId = packet.mapId
        mc.execute { DungeonMapPlayers.sampleMarkers() }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        mapId = null
    }
}
