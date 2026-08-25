package gobby.features.dungeons

import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.skyblock.dungeon.DungeonUtils.getRelativeCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.skyblock.dungeon.tiles.Room
import net.minecraft.core.BlockPos
import gobby.utils.ConfigUtils

object EtherwarpRoutes {

    sealed interface Revert {
        data class Added(val room: String, val pos: String) : Revert
        data class Removed(val room: String, val pos: String) : Revert
        data class Cleared(val room: String, val count: Int) : Revert
    }

    private data class RouteFile(
        val rooms: MutableMap<String, MutableList<String>> = mutableMapOf(),
        var lastAction: LastAction? = null
    )

    private data class LastAction(val type: String, val room: String, val positions: List<String>)

    private val config = ConfigUtils.makeConfig("etherwarpTriggerbot", "routes") { RouteFile() }
    private val data get() = config.data

    private fun save() = config.save()

    fun coordStr(pos: BlockPos): String = "${pos.x}, ${pos.y}, ${pos.z}"

    fun relativeStr(room: Room, realPos: BlockPos): String = coordStr(room.getRelativeCoords(realPos))

    fun spots(room: String): List<String> = data.rooms[room].orEmpty()

    fun isSpot(realPos: BlockPos): Boolean {
        val room = ScanUtils.currentRoom ?: return false
        return relativeStr(room, realPos) in spots(room.data.name)
    }

    fun add(room: String, pos: String): Boolean {
        val spots = data.rooms.getOrPut(room) { mutableListOf() }
        if (pos in spots) return false
        spots.add(pos)
        data.lastAction = LastAction("add", room, listOf(pos))
        save()
        return true
    }

    fun remove(room: String, pos: String): Boolean {
        val removed = data.rooms[room]?.remove(pos) == true
        if (!removed) return false
        data.rooms[room]?.takeIf { it.isEmpty() }?.let { data.rooms.remove(room) }
        data.lastAction = LastAction("remove", room, listOf(pos))
        save()
        return true
    }

    fun clear(room: String): Int {
        val cleared = data.rooms.remove(room) ?: return 0
        data.lastAction = LastAction("clearroom", room, cleared)
        save()
        return cleared.size
    }

    fun revert(): Revert? {
        val action = data.lastAction ?: return null
        val result = when (action.type) {
            "add" -> action.positions.first().takeIf { data.rooms[action.room]?.remove(it) == true }
                ?.let { Revert.Removed(action.room, it) }
            "remove" -> action.positions.first().let {
                data.rooms.getOrPut(action.room) { mutableListOf() }.add(it)
                Revert.Added(action.room, it)
            }
            else -> {
                data.rooms.getOrPut(action.room) { mutableListOf() }.addAll(action.positions)
                Revert.Cleared(action.room, action.positions.size)
            }
        }
        data.lastAction = null
        data.rooms[action.room]?.takeIf { it.isEmpty() }?.let { data.rooms.remove(action.room) }
        save()
        return result
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        data.lastAction = null
        save()
    }
}
