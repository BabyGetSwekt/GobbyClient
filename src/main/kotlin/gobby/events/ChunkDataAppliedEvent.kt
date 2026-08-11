package gobby.events

import net.minecraft.world.level.chunk.LevelChunk

class ChunkDataAppliedEvent(
    val chunk: LevelChunk
) : Events()
