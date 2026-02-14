package gobby.events

import net.minecraft.client.world.ClientWorld
import net.minecraft.world.chunk.WorldChunk

class ChunkUnloadEvent(
    val world: ClientWorld,
    val chunk: WorldChunk
) : Events()
