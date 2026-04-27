package gobby.utils

import com.google.gson.reflect.TypeToken
import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.features.skyblock.FreeCam
import gobby.utils.ChatUtils.errorMessage
import gobby.utils.ChatUtils.modMessage
import gobby.utils.copy.ArmorStandCodec
import gobby.utils.copy.BlockPaster
import gobby.utils.copy.BlockStateCodec
import gobby.utils.copy.RegionBlockCopier
import net.minecraft.block.BlockState
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnReason
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.StringNbtReader
import net.minecraft.util.math.BlockPos
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min

object StructureCopier : RegionBlockCopier() {

    private val schematicsDir = File("./config/gobbyclientFabric/schematics")
    private val LOGGER = LoggerFactory.getLogger("StructureCopier")

    private const val PASTE_BATCH_SIZE = 500
    private const val COMMANDS_PER_TICK = 5

    private data class ArmorStandData(val nbt: String)
    private class StructureData(
        val origin: IntArray,
        val blocks: Map<String, List<IntArray>>,
        val blockEntities: List<BlockPaster.BlockEntityJson>?,
        val armorStands: List<ArmorStandData>?
    )

    var pos1: BlockPos? = null
        private set
    var pos2: BlockPos? = null
        private set

    private val cachedArmorStands = mutableListOf<ArmorStandData>()
    private val commandQueue = ArrayDeque<String>()
    private var remotePasteActive = false

    private fun currentPos(): BlockPos? {
        val player = mc.player ?: return null
        return if (FreeCam.enabled) BlockPos(FreeCam.camX.toInt(), FreeCam.camY.toInt(), FreeCam.camZ.toInt())
        else BlockPos(player.x.toInt(), player.y.toInt(), player.z.toInt())
    }

    fun setPos1() {
        val p = currentPos() ?: run { errorMessage("No player"); return }
        pos1 = p
        modMessage("§aPos 1 set to §f(${p.x}, ${p.y}, ${p.z})")
        tryStart()
    }

    fun setPos2() {
        val p = currentPos() ?: run { errorMessage("No player"); return }
        pos2 = p
        modMessage("§aPos 2 set to §f(${p.x}, ${p.y}, ${p.z})")
        tryStart()
    }

    fun stop() {
        stopScan()
        pos1 = null
        pos2 = null
        clearCache()
        cachedArmorStands.clear()
        if (remotePasteActive) {
            remotePasteActive = false
            commandQueue.clear()
            modMessage("§cRemote paste cancelled")
        }
        modMessage("§cStructure clipboard cleared")
    }

    private fun tryStart() {
        val p1 = pos1 ?: return
        val p2 = pos2 ?: return
        clearCache()
        cachedArmorStands.clear()
        modMessage("§eCopying structure between §f(${p1.x}, ${p1.y}, ${p1.z}) §eand §f(${p2.x}, ${p2.y}, ${p2.z})")
        startScan(min(p1.x, p2.x), max(p1.x, p2.x), min(p1.z, p2.z), max(p1.z, p2.z))
        modMessage("§eWalk around to load $totalChunks chunks...")
    }

    override fun chunkBounds(cx: Int, cz: Int): IntBounds? {
        val p1 = pos1 ?: return null
        val p2 = pos2 ?: return null
        return IntBounds(
            max(cx shl 4, min(p1.x, p2.x)),
            min((cx shl 4) + 15, max(p1.x, p2.x)),
            min(p1.y, p2.y), max(p1.y, p2.y),
            max(cz shl 4, min(p1.z, p2.z)),
            min((cz shl 4) + 15, max(p1.z, p2.z))
        )
    }

    override fun scanExtras(cx: Int, cz: Int) {
        val world = mc.world ?: return
        val p1 = pos1 ?: return
        val p2 = pos2 ?: return
        val minX = min(p1.x, p2.x).toDouble(); val maxX = (max(p1.x, p2.x) + 1).toDouble()
        val minY = min(p1.y, p2.y).toDouble(); val maxY = (max(p1.y, p2.y) + 1).toDouble()
        val minZ = min(p1.z, p2.z).toDouble(); val maxZ = (max(p1.z, p2.z) + 1).toDouble()
        val chunkMinX = (cx shl 4).toDouble(); val chunkMaxX = chunkMinX + 16.0
        val chunkMinZ = (cz shl 4).toDouble(); val chunkMaxZ = chunkMinZ + 16.0

        for (entity in world.entities) {
            if (entity !is ArmorStandEntity) continue
            val ex = entity.x; val ey = entity.y; val ez = entity.z
            if (ex !in chunkMinX..chunkMaxX || ez !in chunkMinZ..chunkMaxZ) continue
            if (ex !in minX..maxX || ey !in minY..maxY || ez !in minZ..maxZ) continue
            ArmorStandCodec.encode(entity, LOGGER)?.let { cachedArmorStands.add(ArmorStandData(it)) }
        }
    }

    override fun onScanProgress(scanned: Int, total: Int) {
        modMessage("§eScanned $scanned/$total chunks ($blockCount blocks, ${cachedArmorStands.size} armor stands)")
    }

    override fun onScanComplete() {
        modMessage("§aStructure scan complete")
        val p1 = pos1 ?: return
        val p2 = pos2 ?: return
        val origin = intArrayOf(min(p1.x, p2.x), min(p1.y, p2.y), min(p1.z, p2.z))

        schematicsDir.mkdirs()
        val fileName = "structure_${System.currentTimeMillis()}.json"
        File(schematicsDir, fileName).writeText(buildJson(origin))

        modMessage("§aSaved §f$blockCount blocks §aand §f${cachedArmorStands.size} armor stands §ato §e$fileName")
        modMessage("§7Join §borange0513.com:30030 §7and run §a/gobby pasteStructure §7to paste.")

        clearCache()
        cachedArmorStands.clear()
    }

    private fun buildJson(origin: IntArray): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"origin\": [${origin[0]},${origin[1]},${origin[2]}],")
        appendBlockEntitiesJson(sb)
        if (cachedArmorStands.isNotEmpty()) sb.appendLine("  \"armorStands\": ${gson.toJson(cachedArmorStands)},")
        appendBlocksJson(sb)
        sb.appendLine("}")
        return sb.toString()
    }

    fun pasteLatest() {
        schematicsDir.mkdirs()
        val latest = schematicsDir.listFiles { f -> f.name.startsWith("structure_") && f.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }
        if (latest == null) { errorMessage("No saved structure found in $schematicsDir"); return }

        val data: StructureData = gson.fromJson(latest.readText(), object : TypeToken<StructureData>() {}.type)
        val allPositions = BlockPaster.decodeAndSort(data.blocks)

        val server = mc.server
        if (server != null) pasteIntegrated(server, data, allPositions, latest.name)
        else pasteRemote(data, allPositions, latest.name)
    }

    private fun pasteIntegrated(
        server: net.minecraft.server.MinecraftServer,
        data: StructureData,
        allPositions: List<Pair<BlockPos, BlockState>>,
        fileName: String
    ) {
        val serverWorld = BlockPaster.overworld(server) ?: return
        BlockPaster.freezeWorld(server)
        modMessage("§ePasting §f${allPositions.size} blocks §eand §f${data.armorStands?.size ?: 0} armor stands §efrom §e$fileName §e(integrated)")

        BlockPaster.pasteBlocks(server, serverWorld, allPositions, PASTE_BATCH_SIZE) {
            BlockPaster.applyBlockEntities(server, serverWorld, data.blockEntities, LOGGER)
            data.armorStands?.forEach { stand ->
                try {
                    val nbt = StringNbtReader.readCompound(stand.nbt)
                    val entity = EntityType.loadEntityWithPassengers(nbt, serverWorld, SpawnReason.LOAD) { it }
                    if (entity is ArmorStandEntity) serverWorld.spawnEntity(entity)
                } catch (_: Exception) {}
            }
            BlockPaster.reloadClientChunks()
            modMessage("§aPasted §f${allPositions.size} blocks §aand §f${data.armorStands?.size ?: 0} armor stands")
        }
    }

    private fun pasteRemote(
        data: StructureData,
        allPositions: List<Pair<BlockPos, BlockState>>,
        fileName: String
    ) {
        if (remotePasteActive) { errorMessage("A remote paste is already in progress. Run /gobby copyStructure stop to cancel."); return }
        commandQueue.clear()
        for ((pos, state) in allPositions) commandQueue.add("setblock ${pos.x} ${pos.y} ${pos.z} ${BlockStateCodec.encode(state)} replace")
        data.armorStands?.forEach { stand ->
            try {
                val nbt = StringNbtReader.readCompound(stand.nbt)
                val pos = nbt.get("Pos") as? NbtList ?: return@forEach
                val x = pos.getDouble(0).orElse(0.0)
                val y = pos.getDouble(1).orElse(0.0)
                val z = pos.getDouble(2).orElse(0.0)
                nbt.remove("UUID"); nbt.remove("Pos"); nbt.remove("id")
                commandQueue.add("summon minecraft:armor_stand $x $y $z $nbt")
            } catch (_: Exception) {}
        }
        remotePasteActive = true
        modMessage("§ePasting §f${allPositions.size} blocks §eand §f${data.armorStands?.size ?: 0} armor stands §efrom §e$fileName §e(remote, via commands)")
        modMessage("§7~${commandQueue.size / COMMANDS_PER_TICK / 20} seconds at $COMMANDS_PER_TICK cmd/tick. Run §c/gobby copyStructure stop §7to cancel.")
    }

    @SubscribeEvent
    fun onTickRemotePaste(event: ClientTickEvent.Post) {
        if (!remotePasteActive) return
        val handler = mc.networkHandler ?: return
        var sent = 0
        while (sent < COMMANDS_PER_TICK && commandQueue.isNotEmpty()) {
            handler.sendChatCommand(commandQueue.removeFirst())
            sent++
        }
        if (commandQueue.isEmpty()) {
            remotePasteActive = false
            modMessage("§aRemote paste complete")
        }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        if (pos1 != null || pos2 != null || isScanning) {
            stopScan()
            pos1 = null; pos2 = null
            clearCache()
            cachedArmorStands.clear()
        }
    }
}
