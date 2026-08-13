package gobby.pathfinder.world

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object BlockCacheDump {

    private const val MAGIC = 0x47424344
    private const val VERSION = 1

    class Dump(val minY: Int, val maxY: Int, val blocks: Map<Long, BlockState>) {
        fun positions(): List<BlockPos> = blocks.keys.map { BlockPos.of(it) }
    }

    fun write(snapshot: BlockCache.SnapshotView, path: Path, minY: Int, maxY: Int): Int {
        val entries = collect(snapshot, minY, maxY)
        path.parent?.let { Files.createDirectories(it) }
        DataOutputStream(GZIPOutputStream(BufferedOutputStream(Files.newOutputStream(path)))).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeInt(minY)
            out.writeInt(maxY)
            out.writeInt(entries.size)
            entries.forEach { entry ->
                out.writeLong(entry.first)
                out.writeInt(entry.second)
            }
        }
        return entries.size
    }

    fun read(path: Path): Dump? {
        if (!Files.exists(path)) return null
        DataInputStream(GZIPInputStream(BufferedInputStream(Files.newInputStream(path)))).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
            val minY = input.readInt()
            val maxY = input.readInt()
            val count = input.readInt()
            val blocks = HashMap<Long, BlockState>(count)
            repeat(count) {
                val packed = input.readLong()
                blocks[packed] = Block.stateById(input.readInt())
            }
            return Dump(minY, maxY, blocks)
        }
    }

    private fun collect(snapshot: BlockCache.SnapshotView, minY: Int, maxY: Int): List<Pair<Long, Int>> =
        snapshot.knownChunkKeys().flatMap { key ->
            snapshot.nonAirCandidates(key)
                .filter { it.y in minY..maxY }
                .mapNotNull { pos -> snapshot.stateAt(pos)?.let { pos.asLong() to Block.getId(it) } }
        }
}
