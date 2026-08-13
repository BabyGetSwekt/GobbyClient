package gobby.utils.skyblock

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.state.BlockState

data class EtherwarpWorldAccess(
    val minY: Int,
    val maxY: Int,
    val blockSource: (BlockPos) -> BlockState?,
    val collisionGetter: BlockGetter = EmptyBlockGetter.INSTANCE
)
