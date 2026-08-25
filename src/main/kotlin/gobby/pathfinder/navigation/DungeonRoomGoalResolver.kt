package gobby.pathfinder.navigation

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import net.minecraft.core.BlockPos

internal object DungeonRoomGoalResolver {
    private val goals = WeightedPositiveCache<GoalKey, GoalEntry>(MAX_GOALS * GOAL_BYTES, GoalEntry::estimatedBytes)

    @Synchronized
    fun clear() = goals.clear()

    fun resolve(cell: Int, referenceY: Int, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView, mapRevision: Long = DEFAULT_MAP_REVISION): BlockPos? {
        val target = scannedCell(grid, cell)
        val room = grid.getOrNull(target) as? MapTile.Room ?: return null
        val key = GoalKey(grid.contentHashCode(), snapshot.worldEpoch, mapRevision, target, referenceY, room.data.name, room.core)
        synchronized(goals) { goals.get(key) }?.takeIf { it.dependenciesUnchanged(snapshot) }?.let { return it.goal }
            ?: synchronized(goals) { goals.remove(key) }
        val cells = DungeonRooms.component(grid, target)
        val resolved = CustomRoomLandings.target(room.data)?.let { custom ->
            CustomRoomLandings.resolve(room.data, cells, grid, snapshot, custom)?.takeIf {
                EtherwarpUtils.isEtherwarpable(it, cached = true, snapshot = snapshot)
            }
        } ?: resolveNearest(target, referenceY, cells, grid, snapshot)
        captureEntry(resolved, key, snapshot)?.let { synchronized(goals) { goals.put(key, it) } }
        return resolved
    }

    private fun captureEntry(goal: BlockPos?, key: GoalKey, snapshot: BlockCache.SnapshotView): GoalEntry? {
        if (goal == null || !snapshot.isTracking || snapshot.missingChunksAccessed().isNotEmpty()) return null
        val dependencies = snapshot.chunksAccessed().mapNotNull { chunk ->
            ChunkDependency(chunk, snapshot.chunkRevision(chunk) ?: return null, snapshot.chunkCollisionFingerprint(chunk) ?: return null)
        }
        return GoalEntry(goal, dependencies)
    }

    private fun resolveNearest(target: Int, referenceY: Int, cells: Set<Int>, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView): BlockPos? {
        return resolveAutomaticRoomGoal(referenceY) { goalY ->
            val center = BlockPos(MapGrid.worldX(MapGrid.col(target)), goalY, MapGrid.worldZ(MapGrid.row(target)))
            EtherwarpUtils.nearestEtherwarpable(center, cached = true, snapshot = snapshot) {
                DungeonRooms.containingRoomCell(grid, it.x + CENTER, it.z + CENTER) in cells
            }
        }
    }

    internal fun <T> resolveAutomaticRoomGoal(referenceY: Int, candidateAt: (Int) -> T?): T? {
        candidateAt(referenceY)?.let { return it }
        if (referenceY == DungeonRoomCoordinates.DOOR_BLOCK_SAMPLE_Y) return null
        return candidateAt(DungeonRoomCoordinates.DOOR_BLOCK_SAMPLE_Y)
    }

    private fun scannedCell(grid: Array<MapTile>, cell: Int): Int =
        DungeonRooms.component(grid, cell).firstOrNull { index ->
            (grid.getOrNull(index) as? MapTile.Room)?.core?.let { it != CORE_EMPTY } == true
        } ?: cell

    private const val CENTER = 0.5
    private const val CORE_EMPTY = 0
    private const val MAX_GOALS = 512L
    private const val GOAL_BYTES = 40L
    private const val DEFAULT_MAP_REVISION = 0L
    private data class GoalKey(val gridSignature: Int, val worldEpoch: Long, val mapRevision: Long, val cell: Int, val referenceY: Int, val roomName: String, val core: Int)
    private data class ChunkDependency(val key: Long, val revision: Long, val fingerprint: Long)
    private data class GoalEntry(val goal: BlockPos, val dependencies: List<ChunkDependency>) {
        val estimatedBytes: Long get() = GOAL_BYTES + dependencies.size * CHUNK_BYTES

        fun dependenciesUnchanged(snapshot: BlockCache.SnapshotView): Boolean = dependencies.all {
            snapshot.chunkRevision(it.key) == it.revision && snapshot.chunkCollisionFingerprint(it.key) == it.fingerprint
        }
    }
    private const val CHUNK_BYTES = 32L
}
