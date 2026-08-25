package gobby.pathfinder.etherwarp

import gobby.features.dungeons.RoomPathfinder
import gobby.pathfinder.search.EtherwarpSearchOutcome
import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicLong

internal object PathPlanDiagnostics {
    private val nextPlanId = AtomicLong()
    private val activePlan = ThreadLocal.withInitial { NO_PLAN }
    private val sampleQuota = ThreadLocal.withInitial { HashMap<String, Int>() }

    val enabled: Boolean get() = RoomPathfinder.pathDebug

    fun <T> withPlan(block: () -> T): T {
        val previous = activePlan.get()
        val previousQuota = sampleQuota.get()
        activePlan.set(nextPlanId.incrementAndGet())
        sampleQuota.set(HashMap())
        return try {
            block()
        } finally {
            activePlan.set(previous)
            sampleQuota.set(previousQuota)
        }
    }

    fun withinSampleQuota(category: String, limit: Int = SAMPLES_PER_PLAN): Boolean {
        val used = sampleQuota.get().merge(category, 1, Int::plus) ?: 1
        if (used == limit + 1) emit("$category further occurrences suppressed for this plan")
        return used <= limit
    }

    fun emit(message: String) = println("[GobbyPath#${activePlan.get()}|${Thread.currentThread().name}] $message")

    fun describeVec(position: Vec3): String = "(%.1f,%.1f,%.1f)".format(position.x, position.y, position.z)

    fun describeBlock(position: BlockPos?): String = position?.let { "(${it.x},${it.y},${it.z})" } ?: "none"

    fun describeOutcome(outcome: EtherwarpSearchOutcome): String = when (outcome) {
        is EtherwarpSearchOutcome.Found -> "found(${outcome.path.size - 1} hops)"
        is EtherwarpSearchOutcome.MissingChunks -> "missingChunks(${outcome.keys.size})"
        EtherwarpSearchOutcome.TimedOut -> "timedOut"
        EtherwarpSearchOutcome.Interrupted -> "interrupted"
        EtherwarpSearchOutcome.NoRoute -> "noRoute"
    }

    fun describeBudget(deadline: SearchDeadline): String =
        "elapsed=${deadline.elapsed}ms remaining=${deadline.remainingMillis}ms"

    fun describeRoom(cellIndex: Int, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView): String {
        val x = MapGrid.worldX(MapGrid.col(cellIndex))
        val z = MapGrid.worldZ(MapGrid.row(cellIndex))
        val name = (grid.getOrNull(cellIndex) as? MapTile.Room)?.data?.name ?: "?"
        return "$name@$cellIndex center=($x,$z) snapshot=${snapshot.hasSnapshot(x, z)}"
    }

    fun describeDoor(doorCell: Int?, grid: Array<MapTile>, open: Boolean?): String = doorCell?.let { cell ->
        "door=$cell type=${(grid.getOrNull(cell) as? MapTile.Door)?.type} open=$open"
    } ?: "door=none"

    private const val NO_PLAN = 0L
    private const val SAMPLES_PER_PLAN = 8
}

internal inline fun pathLog(message: () -> String) {
    if (PathPlanDiagnostics.enabled) PathPlanDiagnostics.emit(message())
}
