package gobby.events

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.chunk.LevelChunk

class ChunkUnloadEvent(
    val world: ClientLevel,
    val chunk: LevelChunk
) : Events()
