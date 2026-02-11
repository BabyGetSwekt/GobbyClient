package gobby.events

import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos

class BlockStateChangeEvent (
    val blockPos: BlockPos,
    val oldState: BlockState?,
    val newState: BlockState
) : Events()