package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChunkLoadEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.gui.hud.HudSetting
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.etherwarp.EtherwarpPathfinder
import gobby.pathfinder.etherwarp.DungeonEtherwarpPathfinder
import gobby.pathfinder.etherwarp.DungeonRoomPathfinder
import gobby.pathfinder.etherwarp.DungeonRoomRoutePreparation
import gobby.pathfinder.navigation.DungeonRoomGoalResolver
import gobby.utils.skyblock.dungeon.map.*
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.RoomType
import gobby.utils.timer.Clock

object DungeonMap : Module("Dungeon Map", "Renders a mini-map of the dungeon.", Category.DUNGEONS, defaultEnabled = true) {

    var hasScanned = false
        private set

    @Volatile
    var revision: Long = 0L
        private set
    private var lastPathSignature = Long.MIN_VALUE
    private var isScanning = false
    private var warmedPathfinderRevision = Long.MIN_VALUE
    private var warmingPathfinderRevision = Long.MIN_VALUE

    private const val REFRESH_INTERVAL_MS = 500L

    val grid = Array<MapTile>(GRID_SIZE * GRID_SIZE) { MapTile.Empty }
    private val checkmarks = Array(GRID_SIZE * GRID_SIZE) { MapCheckmark.NONE }
    private val discovered = BooleanArray(GRID_SIZE * GRID_SIZE)
    private val openedDoors = BooleanArray(GRID_SIZE * GRID_SIZE)
    val checkmarksView: Array<MapCheckmark> get() = checkmarks
    val discoveredView: BooleanArray get() = discovered
    val openedDoorsView: BooleanArray get() = openedDoors

    fun isDoorOpened(cell: Int): Boolean = openedDoors.getOrElse(cell) { false }
    private val refreshClock = Clock()

    fun refreshState() {
        MapCheckmarks.update(grid, checkmarks, discovered)
        if (scanMap()) preparePathfinder()
        MapDoors.updateFromMap(grid, openedDoors)
        markLocalRoom()
        updatePathRevision()
    }

    private fun scanMap(): Boolean {
        if (!inDungeons || inBoss || isScanning) return false
        isScanning = true
        return try {
            val complete = MapScanner.scan(grid)
            hasScanned = hasScanned || complete
            complete
        } finally {
            isScanning = false
        }
    }

    private fun markLocalRoom() {
        if (!hasScanned) return
        val player = mc.player ?: return
        val cell = DungeonRooms.containingRoomCell(grid, player.x, player.z) ?: return
        DungeonRooms.component(grid, cell).forEach {
            if (!discovered[it]) {
                discovered[it] = true
                println("[GobbyRoom] mark cell=$it ${(grid[it] as? MapTile.Room)?.data?.name} via playerCell=$cell pos=(${player.x.toInt()},${player.z.toInt()})")
            }
        }
    }

    private val mapHud by HudSetting("Dungeon Map", "Shows the dungeon layout",
        visible = { inDungeons && !inBoss }
    ) { example ->
        if (example) drawExampleMap() else drawMap()
    }

    private val renderNames by BooleanSetting("Room Names", true, desc = "Show room names on the map")
    private val nameScale by NumberSetting("Name Scale", 100, 50, 200, 10, desc = "Scale of room name text")
        .withDependency { renderNames }
    private val renderHeads by BooleanSetting("Player Heads", true, desc = "Show player heads on the dungeon map")
    private val headScale by NumberSetting("Head Scale", 100, 50, 200, 10, desc = "Scale of player heads")
        .withDependency { renderHeads }
    private val renderCheckmarks by BooleanSetting("Checkmarks", true, desc = "Show room cleared status")
    private val legitMode by BooleanSetting("Legit Mode", false, desc = "Mark undiscovered rooms grey instead of revealing their type")

    private fun HudSetting.drawMap() {
        if (!inDungeons || inBoss) return
        val ctx = drawContext ?: return
        if (refreshClock.hasTimePassed(REFRESH_INTERVAL_MS, setTime = true)) refreshState()
        markLocalRoom()
        MapRenderer.drawMap(ctx, grid, checkmarks, discovered, openedDoors, legitMode, renderNames, nameScale, renderCheckmarks, renderHeads, headScale)
        setSize(MapRenderer.getMapSize(), MapRenderer.getMapSize())
    }

    private fun HudSetting.drawExampleMap() {
        val ctx = drawContext ?: return
        val eg = Array<MapTile>(GRID_SIZE * GRID_SIZE) { MapTile.Empty }
        val normal = RoomData("Market", RoomType.NORMAL, emptyList(), 0, 3, 0)
        val puzzle = RoomData("Puzzle", RoomType.PUZZLE, emptyList(), 0, 0, 0)
        val entrance = RoomData("Entrance", RoomType.ENTRANCE, emptyList(), 0, 0, 0)
        val blood = RoomData("Blood", RoomType.BLOOD, emptyList(), 0, 0, 0)

        eg[0] = MapTile.Room(entrance, 0)
        eg[1] = MapTile.Door(DoorType.NORMAL)
        eg[2] = MapTile.Room(normal, 1)
        eg[3] = MapTile.Door(DoorType.WITHER)
        eg[4] = MapTile.Room(puzzle, 2)
        eg[GRID_SIZE] = MapTile.Door(DoorType.ENTRANCE)
        eg[GRID_SIZE + 2] = MapTile.Connection(normal)
        eg[GRID_SIZE * 2] = MapTile.Room(normal, 3)
        eg[GRID_SIZE * 2 + 1] = MapTile.Door(DoorType.NORMAL)
        eg[GRID_SIZE * 2 + 2] = MapTile.Room(normal, 1)
        eg[GRID_SIZE * 2 + 3] = MapTile.Door(DoorType.BLOOD)
        eg[GRID_SIZE * 2 + 4] = MapTile.Room(blood, 4)

        val ec = Array(GRID_SIZE * GRID_SIZE) { MapCheckmark.NONE }
        val ed = BooleanArray(GRID_SIZE * GRID_SIZE) { true }
        val eo = BooleanArray(GRID_SIZE * GRID_SIZE)
        MapRenderer.drawMap(ctx, eg, ec, ed, eo, false, true, 100, false, false, 100)
        setSize(MapRenderer.getMapSize(), MapRenderer.getMapSize())
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (scanMap()) preparePathfinder()
    }

    private fun preparePathfinder() {
        updatePathRevision()
        val bounds = MapGrid.dungeonChunkBounds()
        BlockCache.captureLoadedChunks(bounds[0], bounds[1], bounds[2], bounds[3])
        if (warmedPathfinderRevision != revision && warmingPathfinderRevision != revision) {
            val requestedRevision = revision
            warmingPathfinderRevision = requestedRevision
            EtherwarpPathfinder.preloadAsync(BlockCache.freeze(), grid.copyOf(), requestedRevision) { succeeded ->
                if (warmingPathfinderRevision != requestedRevision) return@preloadAsync
                warmingPathfinderRevision = Long.MIN_VALUE
                if (succeeded && revision == requestedRevision) warmedPathfinderRevision = requestedRevision
            }
        }
        prewarmReachableRoutes()
    }

    fun printGrid() {
        val sb = StringBuilder()
        for (row in 0 until GRID_SIZE step 2) {
            for (col in 0 until GRID_SIZE step 2) {
                val tile = grid[row * GRID_SIZE + col]
                val name = when (tile) {
                    is MapTile.Room -> tile.data.name.take(8)
                    else -> "----"
                }
                sb.append(name.padEnd(10))
            }
            sb.appendLine()
        }
        modMessage("§eDungeon Grid:\n$sb")
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        grid.fill(MapTile.Empty)
        checkmarks.fill(MapCheckmark.NONE)
        discovered.fill(false)
        openedDoors.fill(false)
        MapCheckmarks.reset()
        hasScanned = false
        revision++
        lastPathSignature = Long.MIN_VALUE
        isScanning = false
        warmedPathfinderRevision = Long.MIN_VALUE
        warmingPathfinderRevision = Long.MIN_VALUE
        EtherwarpPathfinder.cancelPreload()
        DungeonRoomGoalResolver.clear()
    }

    private fun updatePathRevision() {
        val signature = grid.contentHashCode().toLong() * SIGNATURE_MULTIPLIER + openedDoors.contentHashCode()
        if (signature == lastPathSignature) return
        lastPathSignature = signature
        revision++
    }

    private fun prewarmReachableRoutes() {
        if (!RoomPathfinder.enabled) return
        val player = mc.player ?: return
        val startCell = DungeonRooms.roomCellAt(grid, player.x, player.z) ?: return
        val reachable = DungeonRoomPathfinder.reachableCellsFrom(grid, startCell) { door ->
            DungeonEtherwarpPathfinder.doorOpen(door) { position -> BlockCache.knownStateAt(position) }
        }
        DungeonRoomRoutePreparation.prepare(reachable)
    }

    private const val SIGNATURE_MULTIPLIER = 1_000_003L
}
