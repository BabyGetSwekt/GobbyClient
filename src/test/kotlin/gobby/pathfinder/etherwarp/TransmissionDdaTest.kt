package gobby.pathfinder.etherwarp

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransmissionDdaTest {

    private val fullCube = listOf(AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))

    private fun world(vararg solid: BlockPos): (BlockPos) -> List<AABB> {
        val blocked = solid.map { it.asLong() }.toHashSet()
        return { pos -> if (pos.asLong() in blocked) fullCube else emptyList() }
    }

    @Test
    fun reachesEndpointThroughEmptySpace() {
        val hit = EtherwarpRaycaster.transmission(Vec3(0.5, 0.5, 0.5), Vec3(0.0, 0.0, 5.0), world())
        assertEquals(BlockPos(0, 0, 5), hit)
    }

    @Test
    fun stopsAtLastPassableBeforeWall() {
        val hit = EtherwarpRaycaster.transmission(Vec3(0.5, 0.5, 0.5), Vec3(0.0, 0.0, 5.0), world(BlockPos(0, 0, 3)))
        assertEquals(BlockPos(0, 0, 2), hit)
    }

    @Test
    fun returnsNullWhenBlockedAtStart() {
        val hit = EtherwarpRaycaster.transmission(Vec3(0.5, 0.5, 0.5), Vec3(0.0, 0.0, 5.0), world(BlockPos(0, 0, 0)))
        assertNull(hit)
    }
}
