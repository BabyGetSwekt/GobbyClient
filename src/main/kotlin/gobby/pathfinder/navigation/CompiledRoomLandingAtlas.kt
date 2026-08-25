package gobby.pathfinder.navigation

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapConstants
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomData
import net.minecraft.core.BlockPos
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

data class PreparedRoomLandings(
    val positions: List<BlockPos>,
    val anchors: List<BlockPos>,
    val edges: List<PreparedDirectedEdge>,
    val failure: AtlasMatchFailure?
) {
    val compatible: Boolean get() = failure == null && positions.isNotEmpty()
    val outgoing: Map<BlockPos, List<PreparedDirectedEdge>> = edges.groupBy(PreparedDirectedEdge::from)
}

data class PreparedDirectedEdge(val from: BlockPos, val to: BlockPos, val yaw: Float, val pitch: Float)

enum class AtlasMatchFailure {
    EMPTY_COMPONENT,
    TILE_MISMATCH,
    MISSING_TOP,
    FRAME_NOT_FOUND,
    RECORD_NOT_FOUND,
    NO_LIVE_LANDINGS
}

object CompiledRoomLandingAtlas {
    private const val RESOURCE = "/assets/gobbyclient/pathfinder/dungeon-room-landings.bin"
    private const val MAX_CACHE_WEIGHT = 48_000L
    private val artifact by lazy { loadArtifact() }
    private val records: Map<Pair<String, String>, List<RoomLandingRecord>> by lazy {
        artifact.records.groupBy { it.name to it.shape }
    }
    internal val recordCount: Int get() = artifact.records.size
    private val liveCache = WeightedPositiveCache<LiveKey, CachedRoomLandings>(MAX_CACHE_WEIGHT) { it.weight.toLong() }
    private var cacheEpoch = Long.MIN_VALUE

    fun preload() {
        records.size
    }

    fun candidates(data: RoomData, component: Set<Int>, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView): PreparedRoomLandings {
        if (component.isEmpty()) return failed(AtlasMatchFailure.EMPTY_COMPONENT)
        val key = LiveKey(snapshot.worldEpoch, grid.contentHashCode(), data.name, data.shape, component.sorted())
        val cached = synchronized(liveCache) {
            if (cacheEpoch != snapshot.worldEpoch) {
                liveCache.clear()
                cacheEpoch = snapshot.worldEpoch
            }
            liveCache.get(key)
        }
        cached?.takeIf { it.dependenciesUnchanged(snapshot) }?.let { return it.landings }
        cached?.let { synchronized(liveCache) { liveCache.removeIfCurrent(key, it) } }
        val matchingRecords = records[data.name to data.shape] ?: return failed(AtlasMatchFailure.RECORD_NOT_FOUND)
        val tracked = snapshot.trackingMissingChunks()
        val frame = RoomFrameLocator.locate(data, component, grid, tracked)
            ?: return failed(AtlasMatchFailure.FRAME_NOT_FOUND)
        val cells = component.mapNotNull { cell ->
            val tile = grid.getOrNull(cell) as? MapTile.Room ?: return failed(AtlasMatchFailure.TILE_MISMATCH)
            if (tile.data.name != data.name || tile.data.shape != data.shape) return failed(AtlasMatchFailure.TILE_MISMATCH)
            val world = BlockPos(MapGrid.worldX(MapGrid.col(cell)), frame.clay.y, MapGrid.worldZ(MapGrid.row(cell)))
            val local = frame.toLocal(world)
            LocalRoomCell(local.x, local.z, tile.core)
        }.sortedWith(compareBy<LocalRoomCell>({ it.z }, { it.x }, { it.core }))
        val record = matchRecord(matchingRecords, cells)
            ?: return failed(AtlasMatchFailure.RECORD_NOT_FOUND)
        val positions = record.landings.asSequence().map(frame::toWorld).filter { inside(it, component, grid) }.distinct().toList()
        if (positions.isEmpty()) return failed(AtlasMatchFailure.NO_LIVE_LANDINGS)
        val positionSet = positions.toHashSet()
        val prepared = PreparedRoomLandings(
            positions,
            record.anchors.asSequence().map(frame::toWorld).filter(positionSet::contains).distinct().toList(),
            record.edges.asSequence().map { edge -> PreparedDirectedEdge(frame.toWorld(edge.from), frame.toWorld(edge.to), frame.toWorldYaw(edge.yaw), edge.pitch) }
                .filter { it.from in positionSet && it.to in positionSet }.distinctBy { it.from to it.to }.toList(),
            null
        )
        cache(key, CachedRoomLandings(prepared, tracked.chunkDependencies()))
        return prepared
    }

    private fun loadArtifact(): RoomLandingArtifact = CompiledRoomLandingAtlas::class.java.getResourceAsStream(RESOURCE)?.let {
        DataInputStream(BufferedInputStream(GZIPInputStream(it))).use(RoomLandingArtifact::read)
    } ?: RoomLandingArtifact(emptyList())

    private fun cache(key: LiveKey, value: CachedRoomLandings) = synchronized(liveCache) {
        if (cacheEpoch == key.epoch) liveCache.put(key, value)
    }


    private fun BlockCache.SnapshotView.chunkDependencies(): Map<Long, ChunkDependency> = chunksAccessed().associateWith { key ->
        ChunkDependency(requireNotNull(chunkRevision(key)), requireNotNull(chunkCollisionFingerprint(key)))
    }

    private fun cellSignature(cells: List<LocalRoomCell>): List<LocalRoomCell> =
        cells.sortedWith(compareBy<LocalRoomCell>({ it.z }, { it.x }, { it.core }))

    internal fun matchRecord(candidates: List<RoomLandingRecord>, cells: List<LocalRoomCell>): RoomLandingRecord? =
        candidates.singleOrNull { matchesLayout(cellSignature(it.cells), cells) }

    private fun matchesLayout(recordCells: List<LocalRoomCell>, liveCells: List<LocalRoomCell>): Boolean =
        recordCells.size == liveCells.size && recordCells.zip(liveCells).all { (record, live) ->
            record.x == live.x && record.z == live.z &&
                (live.core == MapConstants.UNKNOWN_CORE || record.core == live.core)
        }

    private fun inside(position: BlockPos, component: Set<Int>, grid: Array<MapTile>): Boolean = DungeonRooms.containingRoomCell(grid, position.x + CENTER, position.z + CENTER) in component

    private fun failed(failure: AtlasMatchFailure) = PreparedRoomLandings(emptyList(), emptyList(), emptyList(), failure)

    private data class LiveKey(val epoch: Long, val gridHash: Int, val name: String, val shape: String, val component: List<Int>)
    private data class CachedRoomLandings(val landings: PreparedRoomLandings, val dependencies: Map<Long, ChunkDependency>) {
        val weight: Int get() = landings.positions.size + landings.anchors.size + landings.edges.size * EDGE_WEIGHT

        fun dependenciesUnchanged(snapshot: BlockCache.SnapshotView): Boolean = dependencies.all { (key, dependency) ->
            snapshot.chunkRevision(key) == dependency.revision && snapshot.chunkCollisionFingerprint(key) == dependency.fingerprint
        }
    }
    private data class ChunkDependency(val revision: Long, val fingerprint: Long)
    private const val EDGE_WEIGHT = 2
    private const val CENTER = 0.5
}
