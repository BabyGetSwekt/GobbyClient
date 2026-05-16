package gobby.events

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.chunk.LevelChunk

class ChunkLoadEvent(
    val world: ClientLevel,
    val chunk: LevelChunk
) : Events()
