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
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import java.io.File

object DungeonMapSaver : RegionBlockCopier() {

    private val configFile = File("./config/gobbyclientFabric/schematics/roomMap.json")
    private val LOGGER = LoggerFactory.getLogger("DungeonMapSaver")

    private const val MIN_X = -200
    private const val MAX_X = 0
    private const val MIN_Y = 0
    private const val MAX_Y = 140
    private const val MIN_Z = -200
    private const val MAX_Z = 0

    private const val PASTE_BATCH_SIZE = 500
    private const val FLAGS_SILENT = 16 or 2

    private class MapData(
        val spawn: IntArray?,
        val blocks: Map<String, List<IntArray>>,
        val blockEntities: List<BlockPaster.BlockEntityJson>?
    )

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
        for (row in 0 until GRID_SIZE step 2) for (col in 0 until GRID_SIZE step 2) {
            val tile = grid[row * GRID_SIZE + col]
            if (tile is MapTile.Room && tile.data.type == RoomType.ENTRANCE) {
                val roomX = START_X + col * HALF_ROOM
                val roomZ = START_Z + row * HALF_ROOM
                val room = ScanUtils.scanRoom(Vec2(roomX, roomZ)) ?: continue
                val worldPos = room.getRealCoords(BlockPos(15, 73, 16))
                return intArrayOf(worldPos.x, worldPos.y, worldPos.z)
            }
        }
        return null
    }

    private fun buildJson(spawn: IntArray?): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        if (spawn != null) sb.appendLine("  \"spawn\": [${spawn[0]},${spawn[1]},${spawn[2]}],")
        appendBlockEntitiesJson(sb)
        appendBlocksJson(sb)
        sb.appendLine("}")
        return sb.toString()
    }

    fun copyMap() {
        val server = mc.server ?: run { errorMessage("No integrated server found"); return }
        if (!configFile.exists()) { errorMessage("No saved map found"); return }
        val serverWorld = BlockPaster.overworld(server) ?: return

        BlockPaster.freezeWorld(server)
        modMessage("§eSet randomTickSpeed=0, doFireTick=false, doMobSpawning=false")

        val data: MapData = gson.fromJson(configFile.readText(), object : TypeToken<MapData>() {}.type)
        val allPositions = BlockPaster.decodeAndSort(data.blocks)

        modMessage("§eClearing area and pasting ${allPositions.size} blocks...")

        var clearX = MIN_X
        fun clearStep() {
            val air = Blocks.AIR.defaultState
            val pos = BlockPos.Mutable()
            var count = 0
            while (clearX <= MAX_X && count < PASTE_BATCH_SIZE) {
                for (z in MIN_Z..MAX_Z) for (y in MAX_Y downTo MIN_Y) {
                    pos.set(clearX, y, z)
                    if (!serverWorld.getBlockState(pos).isAir) {
                        serverWorld.setBlockState(pos, air, FLAGS_SILENT)
                        count++
                    }
                }
                clearX++
            }
            if (clearX <= MAX_X) {
                server.execute { clearStep() }
                return
            }
            modMessage("§eArea cleared. Pasting ${allPositions.size} blocks...")
            BlockPaster.pasteBlocks(server, serverWorld, allPositions, PASTE_BATCH_SIZE) {
                BlockPaster.applyBlockEntities(server, serverWorld, data.blockEntities, LOGGER)
                BlockPaster.reloadClientChunks()
                modMessage("§aPasted ${allPositions.size} blocks")

                data.spawn?.let { spawn ->
                    val serverPlayer = server.playerManager.getPlayer(mc.player?.uuid ?: return@let)
                    serverPlayer?.teleport(serverWorld, spawn[0] + 0.5, spawn[1].toDouble(), spawn[2] + 0.5, setOf(), 0f, 0f, false)
                    modMessage("§aTeleported to entrance room")
                }
            }
        }
        server.execute { clearStep() }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        stopScan()
        clearCache()
    }
}
