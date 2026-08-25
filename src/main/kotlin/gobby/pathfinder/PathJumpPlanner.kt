package gobby.pathfinder

import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.phys.Vec3
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

internal data class UpcomingJump(val target: Vec3, val isGap: Boolean)

internal object PathJumpPlanner {
    fun findUpcoming(waypoints: List<Vec3>, idx: Int, pos: Vec3, profile: JumpProfile): UpcomingJump? {
        val threshold = profile.stepHeight + STEP_JUMP_MARGIN
        val ceiling = minOf(idx + max(JUMP_LOOKAHEAD_WAYPOINTS, profile.maxSkipCells), waypoints.lastIndex)
        for (i in idx..ceiling) {
            val target = waypoints[i]
            val previous = if (i > 0) waypoints[i - 1] else pos
            val deltaY = target.y - previous.y
            if (!isCandidate(target, previous, pos, deltaY, threshold, profile)) continue
            if (isStepReachable(target, previous, profile)) continue
            if (horizontalDistance(target, pos) > max(JUMP_LOOKAHEAD_DIST, profile.maxHorizontalBlocks + JUMP_REACH_MARGIN)) continue
            return UpcomingJump(target, horizontalDistance(target, previous) > ADJACENT_CELL_DIST)
        }
        return null
    }

    private fun isCandidate(target: Vec3, previous: Vec3, pos: Vec3, deltaY: Double, threshold: Double, profile: JumpProfile): Boolean =
        deltaY > threshold && deltaY <= profile.maxClimb && target.y - pos.y > threshold

    private fun isStepReachable(target: Vec3, previous: Vec3, profile: JumpProfile): Boolean =
        stepReachable(
            floor(target.x).toInt(),
            floor(target.z).toInt(),
            previous.y,
            target.y,
            target.x - previous.x,
            target.z - previous.z,
            profile
        )

    private fun stepReachable(
        x: Int,
        z: Int,
        fromFeetY: Double,
        toFeetY: Double,
        dirX: Double,
        dirZ: Double,
        profile: JumpProfile
    ): Boolean {
        val state = BlockCache.getBlockState(BlockPos(x, floor(fromFeetY + GROUND_TOLERANCE).toInt(), z))
        if (state.block is StairBlock) return stairReachable(state, dirX, dirZ)
        val surfaces = BlockCache.getStandableSurfaces(x, z, fromFeetY + GROUND_TOLERANCE, toFeetY + GROUND_TOLERANCE)
        val lowest = surfaces.minByOrNull { it.feetY } ?: return false
        return lowest.feetY - fromFeetY <= profile.stepHeight + STEP_JUMP_MARGIN
    }

    private fun stairReachable(state: net.minecraft.world.level.block.state.BlockState, dirX: Double, dirZ: Double): Boolean {
        if (state.getValue(StairBlock.HALF) != Half.BOTTOM) return false
        val facing = state.getValue(StairBlock.FACING)
        return facing.stepX * dirX + facing.stepZ * dirZ > 0.0
    }

    private fun horizontalDistance(first: Vec3, second: Vec3): Double {
        val dx = first.x - second.x
        val dz = first.z - second.z
        return sqrt(dx * dx + dz * dz)
    }

    fun terrainNeedsPrejump(player: net.minecraft.client.player.LocalPlayer, dx: Double, dz: Double, distance: Double, profile: JumpProfile): Boolean {
        if (!player.onGround() || distance < 0.3) return false
        val direction = Vec3(dx / distance, 0.0, dz / distance)
        val feetY = player.y
        return PROBE_DISTANCES.any { probe -> probeColumn(player, direction, probe, feetY, profile) }
    }

    private fun probeColumn(player: net.minecraft.client.player.LocalPlayer, direction: Vec3, distance: Double, feetY: Double, profile: JumpProfile): Boolean {
        val x = player.x + direction.x * distance
        val z = player.z + direction.z * distance
        val low = floor(feetY + PROBE_DEPTH_BELOW).toInt()
        val high = floor(feetY + BlockCache.PLAYER_HEIGHT).toInt()
        return (low..high).any { y -> probeBlock(x, z, y, feetY, profile) }
    }

    private fun probeBlock(x: Double, z: Double, y: Int, feetY: Double, profile: JumpProfile): Boolean {
        val cursor = BlockPos(floor(x).toInt(), y, floor(z).toInt())
        val shape = BlockCache.getCollisionShape(cursor)
        if (shape.isEmpty) return false
        val top = y + shape.max(Direction.Axis.Y)
        val bottom = y + shape.min(Direction.Axis.Y)
        val landable = top <= feetY + profile.maxClimb && BlockCache.isBodyClearAt(x, top, z)
        return isRaisedStep(top, feetY, profile, landable) || isBlockedRise(bottom, top, feetY, profile, landable)
    }

    private fun isRaisedStep(top: Double, feetY: Double, profile: JumpProfile, landable: Boolean): Boolean =
        top - feetY > profile.stepHeight + STEP_JUMP_MARGIN && landable && top > feetY + GROUND_TOLERANCE

    private fun isBlockedRise(bottom: Double, top: Double, feetY: Double, profile: JumpProfile, landable: Boolean): Boolean =
        bottom > feetY + GROUND_TOLERANCE && bottom < feetY + BlockCache.PLAYER_HEIGHT &&
            top > feetY + profile.stepHeight + STEP_JUMP_MARGIN && landable
}
