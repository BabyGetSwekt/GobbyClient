package gobby.utils.copy

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ChatUtils.errorMessage
import net.minecraft.block.BlockState
import net.minecraft.nbt.StringNbtReader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.storage.NbtReadView
import net.minecraft.util.ErrorReporter
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameRules
import net.minecraft.world.World
import org.slf4j.Logger

object BlockPaster {

    private const val FLAGS_SILENT = 16 or 2

    class BlockEntityJson(val pos: IntArray, val nbt: String)

    fun overworld(server: MinecraftServer): ServerWorld? {
        val world = server.getWorld(World.OVERWORLD)
        if (world == null) errorMessage("Could not access server world")
        return world
    }

    fun freezeWorld(server: MinecraftServer) {
        server.gameRules.get(GameRules.RANDOM_TICK_SPEED).set(0, server)
        server.gameRules.get(GameRules.DO_FIRE_TICK).set(false, server)
        server.gameRules.get(GameRules.DO_MOB_SPAWNING).set(false, server)
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
        world: ServerWorld,
        positions: List<Pair<BlockPos, BlockState>>,
        batchSize: Int,
        onDone: () -> Unit
    ) {
        var idx = 0
        fun step() {
            val end = (idx + batchSize).coerceAtMost(positions.size)
            while (idx < end) {
                val (pos, state) = positions[idx]
                world.setBlockState(pos, state, FLAGS_SILENT)
                idx++
            }
            if (idx < positions.size) server.execute { step() } else onDone()
        }
        server.execute { step() }
    }

    fun applyBlockEntities(
        server: MinecraftServer,
        world: ServerWorld,
        entries: List<BlockEntityJson>?,
        logger: Logger
    ) {
        entries ?: return
        for (entry in entries) {
            try {
                val pos = BlockPos(entry.pos[0], entry.pos[1], entry.pos[2])
                val be = world.getBlockEntity(pos) ?: continue
                val nbt = StringNbtReader.readCompound(entry.nbt)
                val readView = NbtReadView.create(ErrorReporter.Logging(be.reporterContext, logger), server.registryManager, nbt)
                be.read(readView)
                be.markDirty()
            } catch (_: Exception) {}
        }
    }

    fun reloadClientChunks() {
        mc.execute { mc.worldRenderer.reload() }
    }
}
