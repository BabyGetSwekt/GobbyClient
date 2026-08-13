package gobby.pathfinder.etherwarp

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class RealDungeonHopFieldTest {

    companion object {
        private const val WARMUP_QUERIES = 25
        private const val MEASURED_QUERIES = 51
        private const val QUERY_LIMIT_NANOS = 1_000_000L
        private const val RANGE = 55.0
        private const val STAND_OFFSET = 1.05
        private const val FALL_HEIGHT = 3.4
        private const val MIN_ROOM_SEPARATION = 4
        private const val CORRIDOR_MARGIN = 24
    }

    private fun bootstrap() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @Test
    fun farRoomQueryStaysUnderOneMillisecond() {
        bootstrap()
        val dungeon = RealDungeonCache.loadOrNull() ?: return
        val rooms = dungeon.occupiedRooms()
        println("[FarRoom] floorLandingSpots=${dungeon.floorCandidates.size} occupiedRooms=${rooms.size}")
        val pair = farthestSeparatedPair(rooms) ?: return
        val (startRoom, goalRoom) = pair
        val start = dungeon.landingNearRoom(startRoom.first, startRoom.second) ?: return
        val goal = dungeon.landingNearRoom(goalRoom.first, goalRoom.second) ?: return
        val separation = cellSeparation(startRoom, goalRoom)
        val corridor = dungeon.corridorCandidates(start, goal, CORRIDOR_MARGIN)
        println("[FarRoom] start=$start room=$startRoom goal=$goal room=$goalRoom cellsApart=$separation corridorCandidates=${corridor.size}")

        val buildStart = System.nanoTime()
        val field = EtherwarpHopField.buildForTesting(goal, RANGE, dungeon.access, corridor)
        println("[FarRoom] buildMs=${(System.nanoTime() - buildStart) / 1_000_000.0} nodes=${field?.nodeCount ?: -1}")
        if (field == null) return

        val standing = Vec3(start.x + 0.5, start.y + STAND_OFFSET, start.z + 0.5)
        val falling = Vec3(start.x + 0.5, start.y + FALL_HEIGHT, start.z + 0.5)
        val hops = field.query(standing, RANGE)?.let { it.size - 1 } ?: -1
        println("[FarRoom] hopsFromStart=$hops")
        assertTrue(medianQueryNanos(field, standing, "standing") < QUERY_LIMIT_NANOS)
        assertTrue(medianQueryNanos(field, falling, "midair") < QUERY_LIMIT_NANOS)
    }

    private fun farthestSeparatedPair(rooms: List<Pair<Int, Int>>): Pair<Pair<Int, Int>, Pair<Int, Int>>? =
        rooms.flatMap { a -> rooms.map { b -> a to b } }
            .filter { cellSeparation(it.first, it.second) >= MIN_ROOM_SEPARATION }
            .maxByOrNull { cellSeparation(it.first, it.second) }

    private fun cellSeparation(a: Pair<Int, Int>, b: Pair<Int, Int>): Int =
        abs(a.first - b.first) + abs(a.second - b.second)

    private fun medianQueryNanos(field: EtherwarpHopField.BuiltField, from: Vec3, label: String): Long {
        repeat(WARMUP_QUERIES) { field.query(from, RANGE) }
        val samples = (0 until MEASURED_QUERIES).map {
            val started = System.nanoTime()
            field.query(from, RANGE)
            System.nanoTime() - started
        }.sorted()
        val median = samples[samples.size / 2]
        println("[FarRoom] $label medianUs=${median / 1_000.0} worstUs=${samples.last() / 1_000.0}")
        return median
    }
}
