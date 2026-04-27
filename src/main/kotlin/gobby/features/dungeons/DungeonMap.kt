package gobby.features.dungeons

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
import gobby.utils.skyblock.dungeon.map.*
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.RoomType
import gobby.utils.timer.Clock

object DungeonMap : Module("Dungeon Map", "Renders a mini-map of the dungeon", Category.DUNGEONS, defaultEnabled = true) {

    var hasScanned = false
        private set
    private var isScanning = false

    val grid = Array<MapTile>(GRID_SIZE * GRID_SIZE) { MapTile.Empty }
    private val checkmarks = Array(GRID_SIZE * GRID_SIZE) { MapCheckmark.NONE }
    private val checkmarkClock = Clock()

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

    private fun HudSetting.drawMap() {
        if (!inDungeons || inBoss) return
        val ctx = drawContext ?: return
        if (checkmarkClock.hasTimePassed(500L, setTime = true)) {
            MapCheckmarks.update(grid, checkmarks)
        }
        MapRenderer.drawMap(ctx, grid, checkmarks, renderNames, nameScale, renderCheckmarks, renderHeads, headScale)
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
        MapRenderer.drawMap(ctx, eg, ec, true, 100, false, false, 100)
        setSize(MapRenderer.getMapSize(), MapRenderer.getMapSize())
    }

    @SubscribeEvent
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!inDungeons || inBoss || isScanning || hasScanned) return
        isScanning = true
        hasScanned = MapScanner.scan(grid)
        isScanning = false
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
        MapCheckmarks.reset()
        hasScanned = false
        isScanning = false
    }
}
