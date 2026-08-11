package gobby.pathfinder.etherwarp

import net.minecraft.core.BlockPos
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EtherwarpContextTest {

    private fun context() =
        EtherwarpContext(BlockPos(0, 0, 0), 12.0, 6.7, emptyRaycasts(), 1000L)

    private fun emptyRaycasts() = Raycasts(
        DoubleArray(0), DoubleArray(0), DoubleArray(0), FloatArray(0), FloatArray(0), 1.0,
        IntArray(0), IntArray(0), FloatArray(0), FloatArray(0)
    )

    private fun node(x: Int, g: Double) =
        EtherwarpNode(x.toDouble(), 0.0, 0.0, BlockPos(x, 0, 0), g, 0.0, null, 0f, 0f)

    @Test
    fun exactlyOneWorkerPublishes() {
        val ctx = context()
        val wins = AtomicInteger()
        (0 until 16).map { i -> thread { if (ctx.publish(listOf(node(i, i.toDouble())))) wins.incrementAndGet() } }.forEach { it.join() }
        assertEquals(1, wins.get())
        assertTrue(ctx.solved)
        assertNotNull(ctx.result)
    }

    @Test
    fun activeCountBalancesToDone() {
        val ctx = context()
        repeat(200) { ctx.offer(node(it, it.toDouble())) }
        (0 until 8).map {
            thread {
                while (true) {
                    ctx.next() ?: break
                    ctx.finish()
                }
            }
        }.forEach { it.join() }
        assertTrue(ctx.done)
    }

    @Test
    fun offerKeepsLowestGForSamePosition() {
        val ctx = context()
        ctx.offer(node(5, 10.0))
        ctx.offer(node(5, 3.0))
        ctx.offer(node(5, 7.0))
        val first = ctx.next()
        assertNotNull(first)
        assertEquals(3.0, first.g)
    }
}
