package gobby.pathfinder.world

import gobby.Gobbyclient.Companion.mc
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

object BlockCache {
    data class StandSurface(
        val pos: BlockPos,
        val feetY: Double
    )

    private val cache = HashMap<Long, BlockState>()
    private val supportTopCache = HashMap<Long, List<Double>>()

    const val STEP_HEIGHT = 0.6
    const val MAX_JUMP_RISE = 1.25
    const val PLAYER_WIDTH = 0.6
    const val PLAYER_HEIGHT = 1.8
    const val PLAYER_HALF_WIDTH = PLAYER_WIDTH / 2.0

    private const val CENTER_X = 0.5
    private const val CENTER_Z = 0.5
    private const val BODY_EPSILON = 1.0E-3
    private const val SUPPORT_EPSILON = 0.05
    private const val HORIZONTAL_MARGIN = 1.0E-3

    fun getBlockState(pos: BlockPos): BlockState {
        val key = pos.asLong()
        cache[key]?.let { return it }

        val world = mc.world ?: return Blocks.AIR.defaultState
        val state = world.getBlockState(pos)
        cache[key] = state
        return state
    }

    fun getCollisionShape(pos: BlockPos): VoxelShape {
        val world = mc.world ?: return VoxelShapes.empty()
        return getBlockState(pos).getCollisionShape(world, pos, ShapeContext.absent())
    }

    fun getCollisionHeight(pos: BlockPos): Double {
        val shape = getCollisionShape(pos)
        return if (shape.isEmpty) 0.0 else shape.boundingBox.maxY
    }

    fun getSupportTopYs(pos: BlockPos): List<Double> {
        val key = pos.asLong()
        supportTopCache[key]?.let { localTops ->
            return localTops.map { pos.y + it }
        }

        val shape = getCollisionShape(pos)
        if (shape.isEmpty) {
            supportTopCache[key] = emptyList()
            return emptyList()
        }

        val localTops = shape.boundingBoxes
            .asSequence()
            .filter { box ->
                !(CENTER_X + BODY_EPSILON < box.minX || CENTER_X - BODY_EPSILON > box.maxX) &&
                    !(CENTER_Z + BODY_EPSILON < box.minZ || CENTER_Z - BODY_EPSILON > box.maxZ)
            }
            .map { ((it.maxY * 16.0).roundToInt()) / 16.0 }
            .distinct()
            .sortedDescending()
            .toList()

        supportTopCache[key] = localTops
        return localTops.map { pos.y + it }
    }

    fun getSupportTopY(pos: BlockPos): Double? {
        return getSupportTopYs(pos).firstOrNull()
    }

    fun quantizeFeetOffset(pos: BlockPos, feetY: Double): Int {
        return ((feetY - pos.y) * 16.0).roundToInt()
    }

    private fun buildPlayerBox(centerX: Double, feetY: Double, centerZ: Double): Box {
        return Box(
            centerX - PLAYER_HALF_WIDTH + HORIZONTAL_MARGIN,
            feetY + BODY_EPSILON,
            centerZ - PLAYER_HALF_WIDTH + HORIZONTAL_MARGIN,
            centerX + PLAYER_HALF_WIDTH - HORIZONTAL_MARGIN,
            feetY + PLAYER_HEIGHT - BODY_EPSILON,
            centerZ + PLAYER_HALF_WIDTH - HORIZONTAL_MARGIN
        )
    }

    private fun hasBlockCollision(box: Box): Boolean {
        val world = mc.world ?: return false
        return world.getBlockCollisions(null, box).iterator().hasNext()
    }

    fun isBodyClearAt(centerX: Double, feetY: Double, centerZ: Double): Boolean {
        return !hasBlockCollision(buildPlayerBox(centerX, feetY, centerZ))
    }

    fun isStandable(pos: BlockPos, feetY: Double): Boolean {
        if (floor(feetY + BODY_EPSILON).toInt() != pos.y) return false

        val bodyBox = buildPlayerBox(pos.x + CENTER_X, feetY, pos.z + CENTER_Z)
        if (hasBlockCollision(bodyBox)) return false

        val supportBox = Box(
            bodyBox.minX,
            feetY - SUPPORT_EPSILON,
            bodyBox.minZ,
            bodyBox.maxX,
            feetY + BODY_EPSILON,
            bodyBox.maxZ
        )
        return hasBlockCollision(supportBox)
    }

    fun resolveStandingSurface(pos: BlockPos): StandSurface? {
        val candidates = linkedSetOf<Double>()

        getSupportTopYs(pos).forEach { topY ->
            if (topY - pos.y <= STEP_HEIGHT + BODY_EPSILON) {
                candidates.add(topY)
            }
        }
        getSupportTopYs(pos.down()).forEach(candidates::add)

        for (feetY in candidates.sortedDescending()) {
            if (isStandable(pos, feetY)) {
                return StandSurface(pos, feetY)
            }
        }
        return null
    }

    fun getStandableSurfaces(x: Int, z: Int, minFeetY: Double, maxFeetY: Double): List<StandSurface> {
        if (maxFeetY + BODY_EPSILON < minFeetY) return emptyList()

        val surfaces = mutableListOf<StandSurface>()
        val seen = HashSet<Pair<Long, Int>>()
        val minSupportY = floor(minFeetY).toInt() - 1
        val maxSupportY = floor(maxFeetY).toInt() + 1

        for (y in maxSupportY downTo minSupportY) {
            val supportPos = BlockPos(x, y, z)
            for (topY in getSupportTopYs(supportPos)) {
                if (topY + BODY_EPSILON < minFeetY || topY - BODY_EPSILON > maxFeetY) continue

                val feetPos = BlockPos(x, floor(topY + BODY_EPSILON).toInt(), z)
                if (!isStandable(feetPos, topY)) continue

                val key = feetPos.asLong() to quantizeFeetOffset(feetPos, topY)
                if (seen.add(key)) {
                    surfaces.add(StandSurface(feetPos, topY))
                }
            }
        }

        return surfaces.sortedByDescending { it.feetY }
    }

    fun isSweepClear(
        fromX: Double,
        fromFeetY: Double,
        fromZ: Double,
        toX: Double,
        toFeetY: Double,
        toZ: Double,
        steps: Int,
        yAt: (Double) -> Double
    ): Boolean {
        if (steps <= 0) return true
        for (i in 0..steps) {
            val t = i.toDouble() / steps.toDouble()
            val x = fromX + (toX - fromX) * t
            val z = fromZ + (toZ - fromZ) * t
            val feetY = yAt(t)
            if (!isBodyClearAt(x, feetY, z)) return false
        }
        return true
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

    fun clear() {
        cache.clear()
        supportTopCache.clear()
    }

    fun invalidate(pos: BlockPos) {
        cache.remove(pos.asLong())
        supportTopCache.remove(pos.asLong())
    }
}
