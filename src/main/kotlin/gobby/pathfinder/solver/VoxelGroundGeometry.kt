package gobby.pathfinder.solver

import gobby.pathfinder.JumpProfile
import gobby.pathfinder.PathBlacklist
import gobby.pathfinder.STEP_JUMP_MARGIN
import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

internal object VoxelGroundGeometry {
    fun terrainPenalty(x: Int, z: Int, feetY: Double): Double {
        var walls = 0
        var cliffs = 0
        val midY = feetY + BlockCache.PLAYER_HEIGHT * 0.5
        CARDINAL_DX.indices.forEach { index ->
            val ax = x + CARDINAL_DX[index]
            val az = z + CARDINAL_DZ[index]
            if (!BlockCache.isChunkAvailable(ax, az)) return@forEach
            val surfaces = BlockCache.getStandableSurfaces(ax, az, feetY - NEIGHBOR_DROP_LIMIT, feetY + BlockCache.MAX_JUMP_RISE)
            if (surfaces.isNotEmpty()) return@forEach
            if (BlockCache.isBodyClearAt(ax + 0.5, midY, az + 0.5)) cliffs++ else walls++
        }
        val base = walls * WALL_PENALTY_PER_NEIGHBOR + cliffs * CLIFF_PENALTY_PER_NEIGHBOR
        val blacklist = PathBlacklist.penaltyAt(x + 0.5, z + 0.5)
        return base * blacklist + blacklist - 1.0
    }

    fun diagonalAllowed(cur: VoxelGroundSolver.Node, dx: Int, dz: Int, targetFeetY: Double): Boolean {
        val passFeetY = max(cur.feetY, targetFeetY)
        if (!BlockCache.isBodyClearAt(cur.x + dx + 0.5, passFeetY, cur.z + 0.5)) return false
        if (!BlockCache.isBodyClearAt(cur.x + 0.5, passFeetY, cur.z + dz + 0.5)) return false
        val belowFeet = floor(passFeetY - 0.05).toInt()
        return cornerSupported(cur.x + dx, belowFeet, cur.z) || cornerSupported(cur.x, belowFeet, cur.z + dz)
    }

    private fun cornerSupported(x: Int, belowFeet: Int, z: Int): Boolean =
        BlockCache.isSolid(BlockPos(x, belowFeet, z)) || BlockCache.isSolid(BlockPos(x, belowFeet - 1, z))

    fun standableAt(x: Int, y: Int, z: Int, anchorFeetY: Double): BlockCache.StandSurface? =
        BlockCache.getStandableSurfaces(x, z, y - 0.1, y + 1.1).minByOrNull { abs(it.feetY - anchorFeetY) }

    fun fallLandingAt(x: Int, z: Int, fromFeetY: Double): BlockCache.StandSurface? =
        BlockCache.getStandableSurfaces(x, z, fromFeetY - FALL_LIMIT, fromFeetY - 1.1).firstOrNull()

    fun jumpLandingAt(x: Int, z: Int, fromFeetY: Double, profile: JumpProfile): BlockCache.StandSurface? {
        val minFeet = fromFeetY + profile.stepHeight + STEP_JUMP_MARGIN
        val maxFeet = fromFeetY + profile.maxClimb
        return BlockCache.getStandableSurfaces(x, z, minFeet, maxFeet).minByOrNull { abs(it.feetY - maxFeet) }
    }

    fun headroomClear(from: VoxelGroundSolver.Node, targetX: Int, targetFeetY: Double, targetZ: Int): Boolean =
        BlockCache.isBodyClearAt(targetX + 0.5, max(from.feetY, targetFeetY), targetZ + 0.5)

    fun fallClear(from: VoxelGroundSolver.Node, targetX: Int, targetFeetY: Double, targetZ: Int): Boolean {
        val swept = BlockCache.isSweepClear(from.x + 0.5, from.z + 0.5, targetX + 0.5, targetZ + 0.5, 2) { from.feetY }
        if (!swept) return false
        var currentY = from.feetY
        while (currentY > targetFeetY) {
            if (!BlockCache.isBodyClearAt(targetX + 0.5, currentY, targetZ + 0.5)) return false
            currentY -= FALL_SAMPLE_STEP
        }
        return true
    }

    fun jumpArcClear(from: VoxelGroundSolver.Node, targetX: Int, targetFeetY: Double, targetZ: Int, profile: JumpProfile): Boolean {
        val distance = sqrt((targetX - from.x).toDouble().let { it * it } + (targetZ - from.z).toDouble().let { it * it })
        val steps = max(2, ceil(distance / ARC_SAMPLE_DISTANCE).toInt())
        val delta = targetFeetY - from.feetY
        val lift = max(MIN_ARC_LIFT, minOf(profile.jumpHeight, delta + ARC_LIFT_MARGIN))
        return BlockCache.isSweepClear(from.x + 0.5, from.z + 0.5, targetX + 0.5, targetZ + 0.5, steps) { t ->
            from.feetY + delta * t + ARC_CURVE_FACTOR * lift * t * (1.0 - t)
        }
    }

    private const val FALL_LIMIT = 64
    private const val FALL_SAMPLE_STEP = 0.5
    private const val ARC_SAMPLE_DISTANCE = 0.35
    private const val ARC_CURVE_FACTOR = 4.0
    private const val MIN_ARC_LIFT = 0.6
    private const val ARC_LIFT_MARGIN = 0.8
    private const val NEIGHBOR_DROP_LIMIT = 2.0
    private const val WALL_PENALTY_PER_NEIGHBOR = 0.8
    private const val CLIFF_PENALTY_PER_NEIGHBOR = 1.5
    private val CARDINAL_DX = intArrayOf(1, -1, 0, 0)
    private val CARDINAL_DZ = intArrayOf(0, 0, 1, -1)
}
