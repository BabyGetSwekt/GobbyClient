package gobby.pathfinder.etherwarp

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.test.Test

class RealDungeonSearchTest {

    companion object {
        private const val RANGE = 55.0
        private const val STAND_OFFSET = 1.05
        private const val MIN_ROOM_SEPARATION = 4
        private const val GOAL_TOLERANCE = 2
    }

    @Test
    fun searchCostAcrossRoomDistances() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val dungeon = RealDungeonCache.loadOrNull() ?: return
        val rooms = dungeon.occupiedRooms()
        val origin = rooms.first()
        val start = dungeon.landingNearRoom(origin.first, origin.second) ?: return
        val from = Vec3(start.x + 0.5, start.y + STAND_OFFSET, start.z + 0.5)
        val access = dungeon.access
        println("[Search] startRoom=$origin start=$start rooms=${rooms.size}")

        rooms.asSequence()
            .filter { separation(origin, it) >= MIN_ROOM_SEPARATION }
            .sortedBy { separation(origin, it) }
            .forEach { room ->
                val goal = dungeon.landingNearRoom(room.first, room.second) ?: return@forEach
                val config = EtherwarpPathConfig(etherRange = RANGE)
                val deadline = SearchDeadline(config.timeout)
                val started = System.nanoTime()
                val path = EtherwarpPathfinder.searchWith(from, goal, EtherwarpKind.ETHERWARP, config, reached(goal), access, deadline)
                val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
                println("[Search] cells=${separation(origin, room)} room=$room ms=$elapsedMs hops=${path?.let { it.size - 1 } ?: -1} timedOut=${deadline.expired}")
            }
    }

    private fun reached(goal: BlockPos): (BlockPos) -> Boolean = { pos ->
        abs(pos.x - goal.x) <= GOAL_TOLERANCE && abs(pos.z - goal.z) <= GOAL_TOLERANCE && abs(pos.y - goal.y) <= GOAL_TOLERANCE
    }

    private fun separation(a: Pair<Int, Int>, b: Pair<Int, Int>): Int = abs(a.first - b.first) + abs(a.second - b.second)
}
