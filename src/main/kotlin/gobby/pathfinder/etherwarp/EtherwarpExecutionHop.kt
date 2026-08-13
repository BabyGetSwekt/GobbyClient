package gobby.pathfinder.etherwarp

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

internal data class EtherwarpExecutionHop(
    val label: Int,
    val expected: Vec3,
    val block: BlockPos,
    val aim: Aim,
    val from: Vec3,
    val firedTick: Int,
    val sneakSent: Boolean,
    val crouching: Boolean,
    val onGround: Boolean
)
