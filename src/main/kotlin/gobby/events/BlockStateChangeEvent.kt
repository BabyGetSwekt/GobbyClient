package gobby.events

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos

class BlockStateChangeEvent (
    val blockPos: BlockPos,
    val oldState: BlockState?,
    val newState: BlockState
) : Events.Cancelable<Unit>()