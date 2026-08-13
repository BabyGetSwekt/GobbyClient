package gobby.pathfinder.etherwarp

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.block.Blocks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoorKnowledgeTest {

    private val doorCell = 24

    private fun bootstrap() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @Test
    fun unknownChunkIsNeverTreatedAsOpen() {
        bootstrap()
        assertFalse(DungeonEtherwarpPathfinder.doorOpen(doorCell) { null })
    }

    @Test
    fun airMeansOpen() {
        bootstrap()
        assertTrue(DungeonEtherwarpPathfinder.doorOpen(doorCell) { Blocks.AIR.defaultBlockState() })
    }

    @Test
    fun witherDoorBlockMeansClosed() {
        bootstrap()
        assertFalse(DungeonEtherwarpPathfinder.doorOpen(doorCell) { Blocks.COAL_BLOCK.defaultBlockState() })
    }
}
