package gobby.pathfinder.etherwarp

import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RaycastMathTest {

    private val unitBox = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

    private fun hits(eye: Vec3, ray: Vec3): Boolean =
        EtherwarpRaycaster.intersects(eye, ray, unitBox[0], unitBox[1], unitBox[2], unitBox[3], unitBox[4], unitBox[5])

    @Test
    fun rayEntersBox() {
        assertTrue(hits(Vec3(0.5, 0.5, -2.0), Vec3(0.0, 0.0, 4.0)))
    }

    @Test
    fun rayTooShortMissesBox() {
        assertFalse(hits(Vec3(0.5, 0.5, -2.0), Vec3(0.0, 0.0, 1.0)))
    }

    @Test
    fun raySidewaysMissesBox() {
        assertFalse(hits(Vec3(5.0, 0.5, -2.0), Vec3(0.0, 0.0, 4.0)))
    }

    @Test
    fun originInsideBoxHits() {
        assertTrue(hits(Vec3(0.5, 0.5, 0.5), Vec3(0.0, 0.0, 1.0)))
    }

    @Test
    fun generatedRaycastsAreScaledUnitVectors() {
        val range = 12.0
        val rc = Raycasts.generate(7f, 6f, range)
        assertTrue(rc.size > 0)
        assertEquals(range, rc.scale)
        for (i in 0 until rc.size) {
            val length = sqrt(rc.dx[i] * rc.dx[i] + rc.dy[i] * rc.dy[i] + rc.dz[i] * rc.dz[i])
            assertEquals(range, length, 1e-6)
            assertTrue(rc.yaws[i] in 0f..360f)
            assertTrue(rc.pitches[i] in -90f..90f)
        }
    }
}
