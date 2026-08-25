package gobby.utils.skyblock.dungeon

import com.google.gson.reflect.TypeToken
import gobby.Gobbyclient.Companion.mc
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.features.dungeons.DungeonMap
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.VecUtils.Vec2
import gobby.utils.copy.BlockPaster
import gobby.utils.copy.RegionBlockCopier
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.map.MapConstants.GRID_SIZE
import gobby.utils.skyblock.dungeon.map.MapConstants.HALF_ROOM
import gobby.utils.skyblock.dungeon.map.MapConstants.START_X
import gobby.utils.skyblock.dungeon.map.MapConstants.START_Z
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.block.Blocks
import org.slf4j.LoggerFactory
import java.io.File

object DungeonMapSaver : RegionBlockCopier() {
    private val configFile = File("./config/gobbyclientFabric/schematics/roomMap.json")
    private val logger = LoggerFactory.getLogger("DungeonMapSaver")
    private const val MIN_X = -200
    private const val MAX_X = 0
    private const val MIN_Y = 0
    private const val MAX_Y = 140
    private const val MIN_Z = -200
    private const val MAX_Z = 0
    private const val PASTE_BATCH_SIZE = 500
    private const val FLAGS_SILENT = 16 or 2
    private const val ROOM_GRID_STEP = 2
    private const val SPAWN_X = 15
    private const val SPAWN_Y = 73
    private const val SPAWN_Z = 16
    private const val CLEAR_WIDTH = MAX_X - MIN_X + 1
    private const val CLEAR_DEPTH = MAX_Z - MIN_Z + 1
    private const val CLEAR_HEIGHT = MAX_Y - MIN_Y + 1

    private class MapData(val spawn: IntArray?, val blocks: Map<String, List<IntArray>>, val blockEntities: List<BlockPaster.BlockEntityJson>?)

    fun startScan() {
        clearCache()
        modMessage("§eStarted dungeon scan. Walk around to load all chunks.")
        startScan(MIN_X, MAX_X, MIN_Z, MAX_Z)
    }

    override fun chunkBounds(cx: Int, cz: Int): IntBounds {
        val startX = (cx shl 4).coerceAtLeast(MIN_X)
        val endX = ((cx shl 4) + 15).coerceAtMost(MAX_X)
        val startZ = (cz shl 4).coerceAtLeast(MIN_Z)
        val endZ = ((cz shl 4) + 15).coerceAtMost(MAX_Z)
        return IntBounds(startX, endX, MIN_Y, MAX_Y, startZ, endZ)
    }

    override fun onScanProgress(scanned: Int, total: Int) {
        modMessage("§eScanned $scanned/$total chunks ($blockCount blocks)")
    }

    override fun onScanComplete() {
        modMessage("§aThe whole dungeon is scanned.")
        modMessage("§eSaving dungeon map... this may take a moment")
        val spawn = findEntranceSpawn()
        configFile.parentFile.mkdirs()
        configFile.writeText(buildJson(spawn))
        modMessage("§aSaved $blockCount blocks to roomMap.json")
        modMessage("§7Run §a/gobby copyMap §7in a singleplayer world to copy the map there.")
        clearCache()
    }

    private fun findEntranceSpawn(): IntArray? {
        val grid = DungeonMap.grid
        for (index in grid.indices) {
            val row = index / GRID_SIZE
            val col = index % GRID_SIZE
            if (row % ROOM_GRID_STEP != 0 || col % ROOM_GRID_STEP != 0) continue
            val tile = grid[index]
            if (tile is MapTile.Room && tile.data.type == RoomType.ENTRANCE) {
                val room = ScanUtils.scanRoom(Vec2(START_X + col * HALF_ROOM, START_Z + row * HALF_ROOM)) ?: continue
                val worldPos = room.getRealCoords(BlockPos(SPAWN_X, SPAWN_Y, SPAWN_Z))
                return intArrayOf(worldPos.x, worldPos.y, worldPos.z)
            }
        }
        return null
    }

    private fun buildJson(spawn: IntArray?): String {
        val json = StringBuilder().appendLine("{")
        if (spawn != null) json.appendLine("  \"spawn\": [${spawn[0]},${spawn[1]},${spawn[2]}],")
        appendBlockEntitiesJson(json)
        appendBlocksJson(json)
        return json.appendLine("}").toString()
    }

    fun copyMap() {
        val server = mc.singleplayerServer ?: run { errorMessage("No integrated server found")
        return }
        if (!configFile.exists()) { errorMessage("No saved map found"); return }
        val world = BlockPaster.overworld(server) ?: return
        BlockPaster.freezeWorld(server)
        modMessage("§eSet randomTickSpeed=0, doFireTick=false, doMobSpawning=false")
        val data: MapData = gson.fromJson(configFile.readText(), object : TypeToken<MapData>() {}.type)
        val positions = BlockPaster.decodeAndSort(data.blocks)
        modMessage("§eClearing area and pasting ${positions.size} blocks...")
        server.execute { clearMap(server, world, data, positions, intArrayOf(0)) }
    }

    private fun clearMap(server: MinecraftServer, world: ServerLevel, data: MapData, positions: List<Pair<BlockPos, net.minecraft.world.level.block.state.BlockState>>, index: IntArray) {
        val air = Blocks.AIR.defaultBlockState()
        val pos = BlockPos.MutableBlockPos()
        var count = 0
        val total = CLEAR_WIDTH * CLEAR_DEPTH * CLEAR_HEIGHT
        while (index[0] < total && count < PASTE_BATCH_SIZE) {
            val columnSize = CLEAR_DEPTH * CLEAR_HEIGHT
            val columnIndex = index[0] % columnSize
            pos.set(MIN_X + index[0] / columnSize, MAX_Y - columnIndex % CLEAR_HEIGHT, MIN_Z + columnIndex / CLEAR_HEIGHT)
            if (!world.getBlockState(pos).isAir) {
                world.setBlock(pos, air, FLAGS_SILENT)
                count++
            }
            index[0]++
        }
        if (index[0] < total) server.execute { clearMap(server, world, data, positions, index) } else pasteMap(server, world, data, positions)
    }

    private fun pasteMap(server: MinecraftServer, world: ServerLevel, data: MapData, positions: List<Pair<BlockPos, net.minecraft.world.level.block.state.BlockState>>) {
        modMessage("§eArea cleared. Pasting ${positions.size} blocks...")
        BlockPaster.pasteBlocks(server, world, positions, PASTE_BATCH_SIZE) {
            BlockPaster.applyBlockEntities(server, world, data.blockEntities, logger)
            BlockPaster.reloadClientChunks()
            modMessage("§aPasted ${positions.size} blocks")
            data.spawn?.let { spawn -> teleportToSpawn(server, world, spawn) }
        }
    }

    private fun teleportToSpawn(server: MinecraftServer, world: ServerLevel, spawn: IntArray) {
        val player = server.playerList.getPlayer(mc.player?.uuid ?: return) ?: return
        player.teleportTo(world, spawn[0] + 0.5, spawn[1].toDouble(), spawn[2] + 0.5, emptySet<Relative>(), 0f, 0f, false)
        modMessage("§aTeleported to entrance room")
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        stopScan()
        clearCache()
    }
}
