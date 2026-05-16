package gobby.features.mining.structurescanner

import gobby.Gobbyclient.Companion.mc
import gobby.events.ChunkLoadEvent
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils
import gobby.utils.render.RenderBeacon
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.world.level.block.Block
import net.minecraft.core.BlockPos
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.LevelChunk

object StructureScanner : Module("Structure Scanner", "Scans loaded chunks for known mining structures and beacons them", Category.MINING) {

    private val notifyChat by BooleanSetting("Chat Notify", true, desc = "Send a chat message when a new structure is found")
    private val onlyOnTargetIsland by BooleanSetting("Island Lock", true, desc = "Only scan when on the structure's island (saves CPU)")
    private val debug by BooleanSetting("Debug", false, desc = "Print scanner diagnostics")

    private val scannedChunks = LongOpenHashSet()
    private val foundByStructure = mutableMapOf<String, MutableList<BlockPos>>()
    private var initialScanDone = false

    private val byFirstBlock: Map<Block, List<Structure>> by lazy {
        Structures.ALL
            .mapNotNull { s -> s.column.firstOrNull()?.block?.let { it to s } }
            .groupBy({ it.first }, { it.second })
    }

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() and 0xFFFFFFFFL) or (cz.toLong() shl 32)

    @SubscribeEvent
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!enabled) return
        if (candidatesForCurrentIsland().isEmpty()) return
        val key = chunkKey(event.chunk.pos.x, event.chunk.pos.z)
        if (!scannedChunks.add(key)) return
        scanChunk(event.chunk)
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!enabled) {
            initialScanDone = false
            return
        }
        if (initialScanDone) return
        if (mc.level == null || mc.player == null) return
        if (LocationUtils.location == "Unknown") return
        initialScanDone = true
        rescanLoaded()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        scannedChunks.clear()
        foundByStructure.clear()
        initialScanDone = false
        RenderBeacon.clearPersistent()
    }

    private fun rescanLoaded() {
        val world = mc.level ?: return
        val player = mc.player ?: return
        val viewDistance = mc.options.renderDistance().get()
        val pcx = player.blockPosition().x shr 4
        val pcz = player.blockPosition().z shr 4
        var scanned = 0
        for (cx in (pcx - viewDistance)..(pcx + viewDistance)) {
            for (cz in (pcz - viewDistance)..(pcz + viewDistance)) {
                val chunk = world.chunkSource.getChunk(cx, cz, false) ?: continue
                if (!scannedChunks.add(chunkKey(cx, cz))) continue
                scanChunk(chunk)
                scanned++
            }
        }
        if (debug) modMessage("§e[SS] §finitial scan: §a$scanned§f chunks, location=§a'${LocationUtils.location}'§f, found=§a${foundByStructure.values.sumOf { it.size }}")
    }

    private fun scanChunk(chunk: LevelChunk) {
        val candidates = candidatesForCurrentIsland()
        if (candidates.isEmpty()) {
            if (debug) modMessage("§e[SS] §7no candidates for location '${LocationUtils.location}'")
            return
        }
        val firstBlocks = candidates.mapNotNull { it.column.firstOrNull()?.block }.toHashSet()
        if (firstBlocks.isEmpty()) return

        val sections = chunk.sections
        val baseX = chunk.pos.minBlockX
        val baseZ = chunk.pos.minBlockZ

        for (i in sections.indices) {
            val section = sections[i]
            val sectionMinY = chunk.minY + (i * 16)
            val sectionMaxY = sectionMinY + 15

            if (!sectionOverlapsAnyRange(sectionMinY, sectionMaxY, candidates)) continue
            if (!sectionContainsAnyOf(section, firstBlocks)) continue

            scanSection(chunk, section, sectionMinY, baseX, baseZ, candidates)
        }
    }

    private fun candidatesForCurrentIsland(): List<Structure> {
        if (!onlyOnTargetIsland) return Structures.ALL
        val location = LocationUtils.location
        return Structures.ALL.filter { it.island == null || it.island == location }
    }

    private fun sectionOverlapsAnyRange(minY: Int, maxY: Int, structures: List<Structure>): Boolean =
        structures.any { it.yRange.first <= maxY && it.yRange.last >= minY }

    private fun sectionContainsAnyOf(section: LevelChunkSection, blocks: Set<Block>): Boolean {
        return section.maybeHas { it.block in blocks }
    }

    private fun scanSection(chunk: LevelChunk, section: LevelChunkSection, sectionMinY: Int, baseX: Int, baseZ: Int, structures: List<Structure>) {
        for (lx in 0..15) {
            val worldX = baseX + lx
            for (ly in 0..15) {
                val worldY = sectionMinY + ly
                for (lz in 0..15) {
                    val state = section.getBlockState(lx, ly, lz)
                    val matches = byFirstBlock[state.block] ?: continue
                    val worldZ = baseZ + lz
                    for (structure in matches) {
                        if (structure !in structures) continue
                        if (worldY !in structure.yRange) continue
                        if (alreadyFound(structure, worldX, worldY, worldZ)) continue
                        if (!matchesColumn(chunk, structure, worldX, worldY, worldZ)) continue
                        registerFind(structure, worldX, worldY, worldZ)
                    }
                }
            }
        }
    }

    private fun matchesColumn(chunk: LevelChunk, structure: Structure, x: Int, y: Int, z: Int): Boolean {
        val world = mc.level ?: return false
        if (!matchesEntry(structure.column[0], chunk.getBlockState(BlockPos(x, y, z)))) return false
        val cursor = BlockPos.MutableBlockPos()
        for (i in 1 until structure.height) {
            cursor.set(x, y + i, z)
            if (!matchesEntry(structure.column[i], world.getBlockState(cursor))) return false
        }
        return true
    }

    private fun matchesEntry(entry: ColumnEntry<*>, state: net.minecraft.world.level.block.state.BlockState): Boolean {
        @Suppress("UNCHECKED_CAST")
        return (entry as ColumnEntry<Comparable<Comparable<*>>>).matches(state)
    }

    private fun alreadyFound(structure: Structure, x: Int, y: Int, z: Int): Boolean {
        val existing = foundByStructure[structure.id] ?: return false
        if (structure.unique) return existing.isNotEmpty()
        if (structure.dedupRadius <= 0) return false
        val waypoint = waypointFor(structure, x, y, z)
        val rSq = structure.dedupRadius.toLong() * structure.dedupRadius.toLong()
        return existing.any { it.distSqr(waypoint) <= rSq }
    }

    private fun waypointFor(structure: Structure, x: Int, y: Int, z: Int): BlockPos = BlockPos(
        x + structure.waypointOffset.x,
        y + structure.waypointOffset.y,
        z + structure.waypointOffset.z
    )

    private fun registerFind(structure: Structure, x: Int, y: Int, z: Int) {
        val waypoint = waypointFor(structure, x, y, z)
        foundByStructure.getOrPut(structure.id) { mutableListOf() }.add(waypoint)
        RenderBeacon.addPersistentBeacon(waypoint, structure.color, structure.displayName)
        if (notifyChat) modMessage("§a${structure.displayName} §7→ §f${waypoint.x}, ${waypoint.y}, ${waypoint.z}")
    }
}
