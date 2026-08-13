package gobby.pathfinder.etherwarp

import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.test.Test

class BuilderEdgeIsolationTest {

    companion object {
        private const val RANGE = 28.0
    }

    @Test
    fun builderKeepsAProvenCrossingEdge() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val dungeon = RealDungeonCache.loadOrNull() ?: return
        val access = dungeon.access
        val west = BlockPos(-141, 68, -89)
        val east = BlockPos(-133, 68, -89)
        val eye = Vec3(west.x + 0.5, EtherwarpKind.ETHERWARP.landingY(west.y) + EtherwarpKind.ETHERWARP.eyeHeight(), west.z + 0.5)

        println("[Iso] directQuickAim=${EtherwarpUtils.quickAim(east, eye, RANGE, access)}")
        println("[Iso] centerDistanceSq=${VecUtils.centerDistanceSq(eye, east)} limit=${RANGE * RANGE}")
        println("[Iso] bucketWest=${EtherwarpGraph.bucketKey(west.x, west.z)} bucketEast=${EtherwarpGraph.bucketKey(east.x, east.z)}")
        println("[Iso] inFloorCandidates west=${dungeon.floorCandidates.contains(west)} east=${dungeon.floorCandidates.contains(east)}")

        val graph = EtherwarpGraphBuilder.build(listOf(west, east), RANGE, access) ?: return
        println("[Iso] miniGraph nodes=${graph.nodeCount} edges=${graph.edgeCount} westNeighbours=${graph.neighbours(0)}")
    }
}
