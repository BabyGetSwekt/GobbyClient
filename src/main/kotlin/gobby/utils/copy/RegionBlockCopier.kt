package gobby.utils.copy

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import gobby.Gobbyclient.Companion.mc
import net.minecraft.util.math.BlockPos

abstract class RegionBlockCopier : ChunkAreaScanner() {

    protected class BlockEntityData(val pos: IntArray, val nbt: String)

    protected val cachedBlocks = mutableMapOf<String, MutableList<IntArray>>()
    protected val cachedBlockEntities = mutableListOf<BlockEntityData>()
    protected var blockCount = 0
        private set

    protected val gson: Gson = GsonBuilder().create()

    protected fun clearCache() {
        cachedBlocks.clear()
        cachedBlockEntities.clear()
        blockCount = 0
    }

    final override fun scanChunk(cx: Int, cz: Int) {
        val world = mc.world ?: return
        val bounds = chunkBounds(cx, cz) ?: return
        val registries = world.registryManager
        val (minX, maxX, minY, maxY, minZ, maxZ) = bounds
        for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
            val pos = BlockPos(x, y, z)
            val state = world.getBlockState(pos)
            if (state.isAir) continue
            cachedBlocks.getOrPut(BlockStateCodec.encode(state)) { mutableListOf() }.add(intArrayOf(x, y, z))
            blockCount++

            val be = world.getBlockEntity(pos) ?: continue
            try {
                cachedBlockEntities.add(BlockEntityData(intArrayOf(x, y, z), be.createNbt(registries).toString()))
            } catch (_: Exception) {}
        }
        scanExtras(cx, cz)
    }

    protected fun appendBlockEntitiesJson(sb: StringBuilder) {
        if (cachedBlockEntities.isEmpty()) return
        sb.appendLine("  \"blockEntities\": [")
        cachedBlockEntities.forEachIndexed { i, be ->
            val comma = if (i < cachedBlockEntities.size - 1) "," else ""
            sb.appendLine("    {\"pos\":[${be.pos[0]},${be.pos[1]},${be.pos[2]}],\"nbt\":${gson.toJson(be.nbt)}}$comma")
        }
        sb.appendLine("  ],")
    }

    protected fun appendBlocksJson(sb: StringBuilder) {
        sb.appendLine("  \"blocks\": {")
        val entries = cachedBlocks.entries.toList()
        entries.forEachIndexed { i, entry ->
            val coords = entry.value.joinToString(",") { "[${it[0]},${it[1]},${it[2]}]" }
            val comma = if (i < entries.size - 1) "," else ""
            sb.appendLine("    \"${entry.key}\": [$coords]$comma")
        }
        sb.appendLine("  }")
    }

    protected abstract fun chunkBounds(cx: Int, cz: Int): IntBounds?
    protected open fun scanExtras(cx: Int, cz: Int) {}

    data class IntBounds(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val minZ: Int, val maxZ: Int)
}
