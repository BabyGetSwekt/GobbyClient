package gobby.pathfinder.navigation

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomData
import net.minecraft.core.BlockPos

object CustomRoomLandings {
    private data class Key(val name: String, val shape: String)
    private data class Spec(val target: LocalLanding? = null, val egress: LocalLanding? = null)

    private val specs by lazy(LazyThreadSafetyMode.PUBLICATION) { mapOf(
        Key("Three Weirdos", "1x1") to Spec(target = LocalLanding(15, -32, 22)),
        Key("Higher Blaze", "1x1") to Spec(LocalLanding(9, -38, 18), LocalLanding(15, -65, 2)),
        Key("Ice Fill", "1x1") to Spec(target = LocalLanding(15, -21, 6)),
        Key("Ice Path", "1x1") to Spec(target = LocalLanding(7, -34, 9)),
        Key("Creeper Beams", "1x1") to Spec(target = LocalLanding(15, -26, 14)),
        Key("Quiz", "1x1") to Spec(target = LocalLanding(15, -37, 5)),
        Key("Water Board", "1x1") to Spec(LocalLanding(15, -42, 14), LocalLanding(15, -32, 6)),
        Key("Tic Tac Toe", "1x1") to Spec(target = LocalLanding(11, -32, 16)),
        Key("Lower Blaze", "1x1") to Spec(LocalLanding(10, -39, 19), LocalLanding(15, -15, 8)),
        Key("Slime", "1x3") to Spec(egress = LocalLanding(47, -31, 7))
    ) }

    fun preload() {
        specs.size
    }

    fun target(data: RoomData): LocalLanding? = specs[Key(data.name, data.shape)]?.target

    fun egress(data: RoomData): LocalLanding? {
        if (Key(data.name, data.shape) !in EGRESS_KEYS) return null
        return specs[Key(data.name, data.shape)]?.egress
    }

    fun resolve(data: RoomData, component: Set<Int>, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView, landing: LocalLanding): BlockPos? =
        RoomFrameLocator.locate(data, component, grid, snapshot)?.toWorld(landing)

    fun policyKey(data: RoomData): Int = target(data)?.hashCode() ?: 0

    private val EGRESS_KEYS = setOf(
        Key("Higher Blaze", "1x1"),
        Key("Water Board", "1x1"),
        Key("Lower Blaze", "1x1"),
        Key("Slime", "1x3")
    )
}
