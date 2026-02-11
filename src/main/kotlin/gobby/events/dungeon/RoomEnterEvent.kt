package gobby.events.dungeon

import gobby.events.Events
import gobby.utils.skyblock.dungeon.tiles.Room

data class RoomEnterEvent(val room: Room?) : Events()