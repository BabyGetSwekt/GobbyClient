package gobby.pathfinder.etherwarp

import net.minecraft.server.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EtherwarpHopFieldPerformanceTest {
    companion object {
        private const val WARMUP_QUERIES = 25
        private const val MEASURED_QUERIES = 31
        private const val QUERY_LIMIT_NANOS = 1_000_000L
        private const val MIN_EXPECTED_HOPS = 5
        private const val RANGE = 55.0
        private const val STANDING_Y = 1.05
        private const val MID_AIR_X = 4.0
        private const val MID_AIR_Y = 1.35
        private const val MID_AIR_Z = 0.5
    }

    @Test
    fun perTickQueryMedianStaysUnderOneMillisecond() {
        val fixture = fixture()
        val buildStart = System.nanoTime()
        val field = assertNotNull(EtherwarpHopField.buildForTesting(fixture.goal, RANGE, fixture.access, fixture.candidates))
        val buildNanos = System.nanoTime() - buildStart
        val from = Vec3(0.5, STANDING_Y, 0.5)

        repeat(WARMUP_QUERIES) { assertNotNull(field.query(from, RANGE)) }
        val samples = (0 until MEASURED_QUERIES).map {
            val start = System.nanoTime()
            assertNotNull(field.query(from, RANGE))
            System.nanoTime() - start
        }.sorted()
        val medianNanos = samples[samples.size / 2]
        val path = field.query(from, RANGE)

        assertNotNull(path)
        assertTrue(path.size - 1 >= MIN_EXPECTED_HOPS)
        assertTrue(medianNanos < QUERY_LIMIT_NANOS)
        println("[EtherwarpBenchmark] buildMs=${buildNanos / 1_000_000.0} queryMedianUs=${medianNanos / 1_000.0} nodes=${field.nodeCount} edges=${field.edgeCount}")
    }

    @Test
    fun midAirQueryResolvesTheForwardHop() {
        val fixture = fixture()
        val field = assertNotNull(EtherwarpHopField.buildForTesting(fixture.goal, RANGE, fixture.access, fixture.candidates))
        val from = Vec3(MID_AIR_X, MID_AIR_Y, MID_AIR_Z)
        val path = assertNotNull(field.query(from, RANGE))

        assertTrue(path.size >= 2)
        assertTrue(path.first().eye == from)
        assertTrue(path[1].pos.x > from.x)
    }

    private fun fixture(): SyntheticDungeonCache {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        return SyntheticDungeonCache()
    }
}
