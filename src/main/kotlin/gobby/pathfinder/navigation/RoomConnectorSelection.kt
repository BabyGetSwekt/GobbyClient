package gobby.pathfinder.navigation

import net.minecraft.core.BlockPos

internal object RoomConnectorSelection {
    fun forRoom(room: PreparedGraphRoom, portals: List<PreparedPortal>): List<BlockPos> =
        room.liveConnectors + portals.asSequence()
            .filter { it.fromCanonical == room.canonical || it.toCanonical == room.canonical }
            .flatMap { it.candidates.asSequence() }
            .toList()
}
