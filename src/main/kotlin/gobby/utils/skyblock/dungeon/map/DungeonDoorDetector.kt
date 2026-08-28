package gobby.utils.skyblock.dungeon.map

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState

object DungeonDoorDetector {

    const val DOOR_BLOCK_Y = 69
    const val DOOR_ARCH_Y = 73
    val ENTRANCE_DOOR_BLOCKS = setOf(
        Blocks.INFESTED_STONE, Blocks.INFESTED_COBBLESTONE, Blocks.INFESTED_STONE_BRICKS,
        Blocks.INFESTED_CHISELED_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS,
        Blocks.INFESTED_MOSSY_STONE_BRICKS, Blocks.INFESTED_DEEPSLATE
    )

    fun detect(x: Int, z: Int, stateAt: (BlockPos) -> BlockState?): MapTile.Door? {
        val atDoorLevel = stateAt(BlockPos(x, DOOR_BLOCK_Y, z)) ?: return null
        closedDoorType(atDoorLevel)?.let { return MapTile.Door(it) }
        if (!atDoorLevel.isAir) return null
        return if (hasDoorArch(x, z, stateAt)) MapTile.Door(DoorType.NORMAL) else null
    }

    fun isOpenFloor(x: Int, z: Int, stateAt: (BlockPos) -> BlockState?): Boolean =
        stateAt(BlockPos(x, DOOR_BLOCK_Y, z))?.isAir == true && !hasDoorArch(x, z, stateAt)

    private fun hasDoorArch(x: Int, z: Int, stateAt: (BlockPos) -> BlockState?): Boolean =
        (DOOR_BLOCK_Y + 1 until DOOR_ARCH_Y).all { stateAt(BlockPos(x, it, z))?.isAir == true } &&
            stateAt(BlockPos(x, DOOR_ARCH_Y, z))?.isAir == false

    private fun closedDoorType(state: BlockState): DoorType? = when {
        state.block == Blocks.COAL_BLOCK -> DoorType.WITHER
        state.block == Blocks.DYED_TERRACOTTA.red() -> DoorType.BLOOD
        state.block in ENTRANCE_DOOR_BLOCKS -> DoorType.ENTRANCE
        else -> null
    }

}
