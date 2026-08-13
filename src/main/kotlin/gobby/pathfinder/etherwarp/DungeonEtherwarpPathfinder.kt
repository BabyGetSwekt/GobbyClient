package gobby.pathfinder.etherwarp

import gobby.features.dungeons.DungeonMap
import gobby.features.dungeons.RoomPathfinder
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.dungeon.map.DoorType
import gobby.utils.skyblock.dungeon.map.DungeonRooms
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.map.RoomGraph
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object DungeonEtherwarpPathfinder {

    @Volatile
    var lastSearchTimedOut = false
        private set

    private const val DOOR_Y = 68
    private const val DOOR_BLOCK_SAMPLE_Y = DOOR_Y + 1
    private const val DOOR_RADIUS = 3.0
    private const val NEXT_ROOM_Y_TOLERANCE = 3
    private const val DOOR_APPROACH_OFFSET = 2
    private const val DIRECT_ATTEMPT_BUDGET_SHARE = 0.2

    fun findDungeonPath(
        from: Vec3,
        to: BlockPos,
        kind: EtherwarpKind = EtherwarpKind.ETHERWARP,
        config: EtherwarpPathConfig = EtherwarpPathConfig(),
        enterRoom: Boolean = true,
        grid: Array<MapTile> = DungeonMap.grid,
        opened: (Int) -> Boolean = DungeonMap::isDoorOpened,
        snapshot: BlockCache.SnapshotView? = null
    ): List<EtherwarpNode>? {
        val cache = snapshot ?: BlockCache.freeze()
        val deadline = SearchDeadline(config.timeout)
        lastSearchTimedOut = false
        val graph = RoomGraph.build(grid)
        val goalBlock = kind.goal(to)
        val startCell = DungeonRooms.roomCellAt(grid, from.x, from.z)
        val goalCell = DungeonRooms.containingRoomCell(grid, to.x + 0.5, to.z + 0.5)
            ?: DungeonRooms.roomCellAt(grid, to.x + 0.5, to.z + 0.5)
        val goalRoom = goalCell?.let { graph.canonical(it) }
        val goalCells = goalRoom?.let { graph.component(it) }.orEmpty()
        val reachGoal = goalReached(goalBlock, goalCells)
        EtherwarpGraphService.pathTo(from, goalBlock, kind.searchRange(config), reachGoal)?.let {
            log("graph hit: ${it.size - 1} hops")
            return it
        }
        val startRoom = startCell?.let { graph.canonical(it) }
        val goalCached = BlockCache.isChunkAvailable(to.x, to.z)
        val goalName = (grid.getOrNull(goalCell ?: -1) as? MapTile.Room)?.data?.name
        log("from=(${from.x.toInt()},${from.z.toInt()}) startCell=$startCell startRoom=$startRoom | to=$to goalBlock=$goalBlock goalCell=$goalCell goalRoom=$goalRoom goal='$goalName' goalCells=${goalCells.size} enterRoom=$enterRoom goalChunkCached=$goalCached range=${kind.searchRange(config)}")

        if (startCell == null || goalCell == null || startRoom == null || goalRoom == null || startRoom == goalRoom) {
            if (enterRoom && startRoom != null && goalRoom != null) {
                requestHopField(goalBlock, kind, config, cache, graph.component(goalRoom))
            }
            val r = singleSegment(from, goalBlock, kind, config, reachGoal, cache, deadline)
            log("degenerate(startCell=$startCell goalCell=$goalCell startRoom=$startRoom goalRoom=$goalRoom sameRoom=${startRoom == goalRoom}) -> singleSegment ${if (r == null) "FAILED" else "ok(${r.size})"}")
            return r
        }
        val rooms = DungeonRoomPathfinder.findPath(grid, graph, startCell, goalCell, opened = opened)
        if (rooms == null) {
            log("room-graph NO path (locked/disconnected doors) -> abort")
            return null
        }
        if (enterRoom) {
            val allowedCells = rooms.asSequence()
                .flatMap { graph.component(it.cellIndex).asSequence() }
                .toSet()
            requestHopField(goalBlock, kind, config, cache, allowedCells)
        }
        log("room path (${rooms.size}): ${rooms.joinToString(" -> ") { "${it.data.name}${it.doorCell?.let { d -> "[door=${(grid.getOrNull(d) as? MapTile.Door)?.type},open=${doorOpen(d) { p -> cache.getBlockState(p) }}]" } ?: ""}" }}")
        rooms.forEach { step ->
            log("cache ${step.data.name} ${BlockCache.debugChunk(MapGrid.worldX(MapGrid.col(step.cellIndex)), MapGrid.worldZ(MapGrid.row(step.cellIndex)))}")
        }
        if (enterRoom) {
            val directDeadline = SearchDeadline((config.timeout * DIRECT_ATTEMPT_BUDGET_SHARE).toLong())
            singleSegment(from, goalBlock, kind, config, reachGoal, cache, directDeadline)?.let {
                log("direct ok(${it.size}) — skipped stitch")
                return it
            }
        }
        val stitched = stitch(from, goalBlock, rooms, graph, goalCells, kind, config, enterRoom, grid, cache, deadline)
        if (stitched != null) {
            log("stitch ok(${stitched.size})")
            return stitched
        }
        lastSearchTimedOut = deadline.expired
        log("stitch FAILED -> abort timedOut=$lastSearchTimedOut")
        return null
    }

    fun hopFieldFor(to: BlockPos, kind: EtherwarpKind, config: EtherwarpPathConfig): EtherwarpHopField.Handle? =
        EtherwarpHopField.forGoal(kind.goal(to), kind.searchRange(config))

    private fun requestHopField(
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        cache: BlockCache.SnapshotView,
        allowedCells: Set<Int>
    ) {
        if (kind == EtherwarpKind.ETHERWARP) {
            EtherwarpHopField.request(goal, kind.searchRange(config), cache, allowedCells)
        }
    }

    private fun log(msg: String) {
        if (RoomPathfinder.pathDebug) println("[GobbyPath] $msg")
    }

    fun doorOpen(doorCell: Int, stateAt: (BlockPos) -> BlockState?): Boolean =
        stateAt(doorSamplePos(doorCell))?.isAir == true

    private fun doorSamplePos(doorCell: Int): BlockPos =
        BlockPos(MapGrid.worldX(MapGrid.col(doorCell)), DOOR_BLOCK_SAMPLE_Y, MapGrid.worldZ(MapGrid.row(doorCell)))

    private fun goalReached(goalBlock: BlockPos, goalCells: Set<Int>): (BlockPos) -> Boolean =
        { it == goalBlock || MapGrid.cellOf(it.x + 0.5, it.z + 0.5) in goalCells }

    private fun singleSegment(from: Vec3, goalBlock: BlockPos, kind: EtherwarpKind, config: EtherwarpPathConfig, reachGoal: (BlockPos) -> Boolean, cache: BlockCache.SnapshotView, deadline: SearchDeadline): List<EtherwarpNode>? =
        EtherwarpPathfinder.search(from, goalBlock, kind, config, reachGoal, cache, deadline)?.let { EtherwarpPathfinder.smooth(it, kind.searchRange(config), kind, cache) }

    private fun stitch(
        from: Vec3,
        goalBlock: BlockPos,
        rooms: List<RoomStep>,
        graph: RoomGraph,
        goalCells: Set<Int>,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        enterRoom: Boolean,
        grid: Array<MapTile>,
        cache: BlockCache.SnapshotView,
        deadline: SearchDeadline
    ): List<EtherwarpNode>? {
        val full = mutableListOf<EtherwarpNode>()
        var origin = from
        val steps = if (enterRoom || rooms.size < 2) rooms.size else rooms.size - 1
        for (i in 0 until steps) {
            val allowEnter = enterRoom || i < steps - 1
            val next = rooms.getOrNull(i + 1)
            val segment = searchSegment(origin, rooms[i], next, graph, goalCells, goalBlock, kind, config, allowEnter, grid, cache, deadline)
            if (segment == null) {
                    log("  segment $i FAILED: ${rooms[i].data.name} -> ${next?.data?.name ?: "goal"} allowEnter=$allowEnter origin=(${origin.x.toInt()},${origin.z.toInt()}) door=${rooms[i].doorCell?.let { c -> doorWorld(c, rooms[i].cellIndex, grid) }}")
                return null
            }
            if (full.isEmpty()) full.addAll(segment) else full.addAll(segment.drop(1))
            origin = (full.lastOrNull() ?: return null).eye
        }
        return EtherwarpPathfinder.smooth(full, kind.searchRange(config), kind, cache)
    }

    private fun searchSegment(
        origin: Vec3,
        step: RoomStep,
        next: RoomStep?,
        graph: RoomGraph,
        goalCells: Set<Int>,
        goalBlock: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        allowEnter: Boolean,
        grid: Array<MapTile>,
        cache: BlockCache.SnapshotView,
        deadline: SearchDeadline
    ): List<EtherwarpNode>? {
        if (step.doorCell == null || next == null) {
            return EtherwarpPathfinder.search(origin, goalBlock, kind, config, goalReached(goalBlock, goalCells), cache, deadline)
        }
        val door = doorWorld(step.doorCell, step.cellIndex, grid)
        val nextCells = graph.component(next.cellIndex)
        if (!allowEnter) return EtherwarpPathfinder.search(origin, door, kind, config, doorOnlyReached(door), cache, deadline)
        val entryGoal = roomEntryGoal(step.doorCell, nextCells, grid, cache) ?: door
        return EtherwarpPathfinder.search(origin, entryGoal, kind, config, doorReached(door, nextCells), cache, deadline)
    }

    private fun roomEntryGoal(doorCell: Int, nextCells: Set<Int>, grid: Array<MapTile>, cache: BlockCache.SnapshotView): BlockPos? {
        val entryCell = entryCellOf(doorCell, nextCells) ?: return null
        val center = BlockPos(MapGrid.worldX(MapGrid.col(entryCell)), DOOR_BLOCK_SAMPLE_Y, MapGrid.worldZ(MapGrid.row(entryCell)))
        return EtherwarpUtils.nearestEtherwarpable(center, cached = true, snapshot = cache) {
            DungeonRooms.containingRoomCell(grid, it.x + 0.5, it.z + 0.5) in nextCells
        }
    }

    private fun entryCellOf(doorCell: Int, nextCells: Set<Int>): Int? {
        val col = MapGrid.col(doorCell)
        val row = MapGrid.row(doorCell)
        val neighbours = if (col and 1 == 1) listOf(MapGrid.index(col - 1, row), MapGrid.index(col + 1, row))
            else listOf(MapGrid.index(col, row - 1), MapGrid.index(col, row + 1))
        return neighbours.firstOrNull { it in nextCells }
    }

    private fun doorOnlyReached(door: BlockPos): (BlockPos) -> Boolean = { pos ->
        VecUtils.distanceSq(pos, door) <= DOOR_RADIUS * DOOR_RADIUS
    }

    private fun doorReached(door: BlockPos, nextCells: Set<Int>): (BlockPos) -> Boolean = { pos ->
        MapGrid.cellOf(pos.x + 0.5, pos.z + 0.5) in nextCells &&
            abs(pos.y - door.y) <= NEXT_ROOM_Y_TOLERANCE
    }

    private fun doorWorld(doorCell: Int, approachCell: Int, grid: Array<MapTile>): BlockPos {
        val x = MapGrid.worldX(MapGrid.col(doorCell))
        val z = MapGrid.worldZ(MapGrid.row(doorCell))
        val door = grid.getOrNull(doorCell) as? MapTile.Door
        if (door == null || (door.type != DoorType.WITHER && door.type != DoorType.BLOOD)) return BlockPos(x, DOOR_Y, z)
        val dx = (MapGrid.col(approachCell) - MapGrid.col(doorCell)).coerceIn(-1, 1)
        val dz = (MapGrid.row(approachCell) - MapGrid.row(doorCell)).coerceIn(-1, 1)
        return BlockPos(x + dx * DOOR_APPROACH_OFFSET, DOOR_Y, z + dz * DOOR_APPROACH_OFFSET)
    }

}
