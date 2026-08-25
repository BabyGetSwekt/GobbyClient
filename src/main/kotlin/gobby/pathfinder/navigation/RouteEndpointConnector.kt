package gobby.pathfinder.navigation

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import gobby.pathfinder.etherwarp.EtherwarpKind

internal object RouteEndpointConnector {
    fun add(rooms: List<PreparedGraphRoom>, from: Vec3, goal: BlockPos, kind: EtherwarpKind): List<PreparedGraphRoom> =
        rooms.mapIndexed { index, room ->
            val endpoints = buildList {
                if (index == 0) add(kind.standingBlock(from))
                if (index == rooms.lastIndex) add(goal)
            }
            room.copy(liveConnectors = (room.liveConnectors + endpoints).distinct())
        }
}
