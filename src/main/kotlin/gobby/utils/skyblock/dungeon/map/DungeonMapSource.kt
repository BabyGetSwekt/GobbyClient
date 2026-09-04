package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient
import gobby.Gobbyclient.Companion.mc
import gobby.events.DungeonMapDataEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket
import net.minecraft.world.item.MapItem
import net.minecraft.world.level.saveddata.maps.MapDecoration
import net.minecraft.world.level.saveddata.maps.MapItemSavedData

object DungeonMapSource {

    private const val MAP_PIXELS = 128 * 128
    private const val EMPTY_CORNER: Byte = 0

    @Volatile
    var colors: ByteArray? = null
        private set

    @Volatile
    var decorations: List<MapDecoration> = emptyList()
        private set

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        val packet = event.packet as? ClientboundMapItemDataPacket ?: return
        val id = packet.mapId
        mc.execute {
            val level = mc.level ?: return@execute
            val data = MapItem.getSavedData(id, level)?.takeIf { it.isDungeonMap } ?: return@execute
            colors = data.colors.clone()
            decorations = data.decorations.toList()
            DungeonMapPlayers.sampleMarkers()
            Gobbyclient.EVENT_MANAGER.publish(DungeonMapDataEvent())
        }
    }

    private val MapItemSavedData.isDungeonMap: Boolean
        get() = colors.size >= MAP_PIXELS && colors[0] == EMPTY_CORNER && colors.any { it != EMPTY_CORNER }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        colors = null
        decorations = emptyList()
    }
}
