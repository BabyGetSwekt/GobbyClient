package gobby.pathfinder.world

import gobby.Gobbyclient.Companion.mc
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.util.math.BlockPos

object BlockCache {
    private val cache = HashMap<Long, BlockState>()

    fun getBlockState(pos: BlockPos): BlockState {
        val key = pos.asLong()
        cache[key]?.let { return it }

        val world = mc.world ?: return Blocks.AIR.defaultState
        val state = world.getBlockState(pos)
        cache[key] = state
        return state
    }

    private const val STEP_HEIGHT = 0.5

    fun getCollisionHeight(pos: BlockPos): Double {
        val world = mc.world ?: return 0.0
        val shape = getBlockState(pos).getCollisionShape(world, pos, ShapeContext.absent())
        return if (shape.isEmpty) 0.0 else shape.boundingBox.maxY
    }

    fun isPassable(pos: BlockPos): Boolean = getCollisionHeight(pos) == 0.0

    fun isSteppable(pos: BlockPos): Boolean = getCollisionHeight(pos) <= STEP_HEIGHT

    fun isSolid(pos: BlockPos): Boolean = getCollisionHeight(pos) > 0.0

    fun isWalkable(pos: BlockPos): Boolean {
        val feetClear = isSteppable(pos)
        val headClear = isPassable(pos.up())
        val groundSolid = isSolid(pos.down())
        return feetClear && headClear && groundSolid
    }

    fun clear() = cache.clear()

    fun invalidate(pos: BlockPos) {
        cache.remove(pos.asLong())
    }
}
