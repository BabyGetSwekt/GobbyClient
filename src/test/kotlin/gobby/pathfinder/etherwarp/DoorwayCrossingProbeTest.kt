package gobby.pathfinder.etherwarp

import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.test.Test

class DoorwayCrossingProbeTest {

    companion object {
        private const val RANGE = 55.0
        private const val DOORWAY_Z = -89
        private const val WALL_X = -137
        private const val PROBE_SPAN = 12
        private const val SCAN_TOP = 90
        private const val SCAN_BOTTOM = 55
    }

    @Test
    fun reportWhyDoorwayCrossingFails() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val dungeon = RealDungeonCache.loadOrNull() ?: return
        val access = dungeon.access

        val west = standableNear(WALL_X - 4, DOORWAY_Z, access)
        val east = standableNear(WALL_X + 4, DOORWAY_Z, access)
        println("[Probe] west=$west east=$east")
        if (west == null || east == null) return

        println("[Probe] westEtherwarpable=${EtherwarpUtils.isEtherwarpable(west, access)} eastEtherwarpable=${EtherwarpUtils.isEtherwarpable(east, access)}")
        val eye = Vec3(west.x + 0.5, EtherwarpKind.ETHERWARP.landingY(west.y) + EtherwarpKind.ETHERWARP.eyeHeight(), west.z + 0.5)
        println("[Probe] eye=$eye quickAim=${EtherwarpUtils.quickAim(east, eye, RANGE, access)}")

        (-PROBE_SPAN..PROBE_SPAN).forEach { dz ->
            val target = standableNear(WALL_X + 4, DOORWAY_Z + dz, access) ?: return@forEach
            val aim = EtherwarpUtils.quickAim(target, eye, RANGE, access)
            if (aim != null) println("[Probe] crossing works at z=${DOORWAY_Z + dz} target=$target")
        }

        val wallColumn = (SCAN_BOTTOM..SCAN_TOP).filter { y -> access.blockSource(BlockPos(WALL_X, y, DOORWAY_Z))?.isAir == false }
        println("[Probe] wallSolidY@z=$DOORWAY_Z -> $wallColumn")
    }

    @Test
    fun reportGraphEdgesAroundDoorway() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val dungeon = RealDungeonCache.loadOrNull() ?: return
        val access = dungeon.access
        val floor = dungeon.floorCandidates.filter { it.y in 66..73 }
        val roomA = floor.filter { it.x in -168..-140 && it.z in -104..-76 }
        println("[Room] landingSpotsInOneRoom=${roomA.size}")
        val dominantY = roomA.groupingBy { it.y }.eachCount().maxByOrNull { it.value }?.key ?: return
        val level = roomA.filter { it.y == dominantY }
        println("[Room] dominantY=$dominantY spotsAtLevel=${level.size} yHistogram=${roomA.groupingBy { it.y }.eachCount()}")
        val reachSummary = level.take(6).map { a ->
            val ae = Vec3(a.x + 0.5, EtherwarpKind.ETHERWARP.landingY(a.y) + EtherwarpKind.ETHERWARP.eyeHeight(), a.z + 0.5)
            val vis = level.count { EtherwarpUtils.quickAim(it, ae, RANGE, access) != null }
            val maxDist = level.filter { EtherwarpUtils.quickAim(it, ae, RANGE, access) != null }
                .maxOfOrNull { kotlin.math.hypot((it.x - a.x).toDouble(), (it.z - a.z).toDouble()) } ?: 0.0
            "$a->vis=$vis/${level.size},maxHop=${"%.1f".format(maxDist)}"
        }
        println("[Room] sameLevelVisibility=$reachSummary")
        val anchor = level.first()
        val eye = Vec3(anchor.x + 0.5, EtherwarpKind.ETHERWARP.landingY(anchor.y) + EtherwarpKind.ETHERWARP.eyeHeight(), anchor.z + 0.5)
        val visible = roomA.count { EtherwarpUtils.quickAim(it, eye, RANGE, access) != null }
        println("[Room] anchor=$anchor visibleFromAnchor=$visible of ${roomA.size}")
        val far = roomA.filter { kotlin.math.abs(it.x - anchor.x) > 20 }
        println("[Room] farSpots=${far.size} visibleFar=${far.count { EtherwarpUtils.quickAim(it, eye, RANGE, access) != null }}")
        val crossers = floor.filter { it.x in -134..-128 && it.z in -92..-86 }
        val westApproach = floor.filter { it.x in -146..-138 && it.z in -92..-86 }
        val links = westApproach.count { w ->
            val we = Vec3(w.x + 0.5, EtherwarpKind.ETHERWARP.landingY(w.y) + EtherwarpKind.ETHERWARP.eyeHeight(), w.z + 0.5)
            crossers.any { EtherwarpUtils.quickAim(it, we, RANGE, access) != null }
        }
        println("[Room] doorwayWest=${westApproach.size} doorwayEast=${crossers.size} westWithCrossing=$links")
    }

    private fun standableNear(x: Int, z: Int, access: gobby.utils.skyblock.EtherwarpWorldAccess): BlockPos? =
        (SCAN_TOP downTo SCAN_BOTTOM).map { BlockPos(x, it, z) }.firstOrNull { EtherwarpUtils.isEtherwarpable(it, access) }
}
