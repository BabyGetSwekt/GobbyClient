package gobby.events

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class BlockStateAppliedEvent(
    val blockPos: BlockPos,
    val newState: BlockState
) : Events()
