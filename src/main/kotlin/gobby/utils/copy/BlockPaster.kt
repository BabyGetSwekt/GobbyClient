package gobby.utils.copy

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.storage.TagValueInput
import net.minecraft.util.ProblemReporter
import net.minecraft.core.BlockPos
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.Level
import org.slf4j.Logger

object BlockPaster {

    private const val FLAGS_SILENT = 16 or 2

    class BlockEntityJson(val pos: IntArray, val nbt: String)

    fun overworld(server: MinecraftServer): ServerLevel? {
        val world = server.getLevel(Level.OVERWORLD)
        if (world == null) errorMessage("Could not access server world")
        return world
    }

    fun freezeWorld(server: MinecraftServer) {
        server.overworld().gameRules.set(GameRules.RANDOM_TICK_SPEED, 0, server)
        server.overworld().gameRules.set(GameRules.TNT_EXPLODES, false, server)
        server.overworld().gameRules.set(GameRules.SPAWN_MOBS, false, server)
    }

    fun decodeAndSort(blocks: Map<String, List<IntArray>>): List<Pair<BlockPos, BlockState>> {
        val out = mutableListOf<Pair<BlockPos, BlockState>>()
        for ((stateStr, positions) in blocks) {
            val state = BlockStateCodec.decode(stateStr) ?: continue
            for (coords in positions) out.add(BlockPos(coords[0], coords[1], coords[2]) to state)
        }
        out.sortBy { it.first.y }
        return out
    }

    fun pasteBlocks(
        server: MinecraftServer,
        world: ServerLevel,
        positions: List<Pair<BlockPos, BlockState>>,
        batchSize: Int,
        onDone: () -> Unit
    ) {
        var idx = 0
        fun step() {
            val end = (idx + batchSize).coerceAtMost(positions.size)
            while (idx < end) {
                val (pos, state) = positions[idx]
                world.setBlock(pos, state, FLAGS_SILENT)
                idx++
            }
            if (idx < positions.size) server.execute { step() } else onDone()
        }
        server.execute { step() }
    }

    fun applyBlockEntities(
        server: MinecraftServer,
        world: ServerLevel,
        entries: List<BlockEntityJson>?,
        logger: Logger
    ) {
        entries ?: return
        for (entry in entries) {
            try {
                val pos = BlockPos(entry.pos[0], entry.pos[1], entry.pos[2])
                val be = world.getBlockEntity(pos) ?: continue
                val nbt = TagParser.parseCompoundFully(entry.nbt)
                val readView = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), nbt)
                be.loadCustomOnly(readView)
                be.setChanged()
            } catch (_: Exception) {}
        }
    }

    fun reloadClientChunks() {
        mc.execute { mc.levelExtractor.allChanged() }
    }
}
