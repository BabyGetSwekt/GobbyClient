package gobby.pathfinder

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import gobby.pathfinder.world.BlockCache
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

internal object PathSteeringTerrain {
    fun pitchFromSlope(waypoints: List<net.minecraft.world.phys.Vec3>, idx: Int, lookDx: Double, lookDz: Double): Double {
        if (idx + 1 >= waypoints.size) return 0.0
        var totalSlope = 0.0
        var totalWeight = 0.0
        var accumulatedDistance = 0.0
        val segmentCount = minOf(PITCH_MAX_SEGMENTS, waypoints.size - idx - 1)
        for (i in 0 until segmentCount) {
            val start = waypoints[idx + i]
            val end = waypoints[idx + i + 1]
            val distance = horizontalDistance(start.x, start.z, end.x, end.z)
            if (distance < 0.01) continue
            accumulatedDistance += distance
            if (accumulatedDistance > PITCH_MAX_DISTANCE) break
            val weight = distance * (1.0 - accumulatedDistance / PITCH_MAX_DISTANCE)
            totalSlope += ((end.y - start.y) / distance) * weight
            totalWeight += weight
        }
        if (totalWeight <= 0.01) return 0.0
        val lookDistance = horizontalDistance(0.0, 0.0, lookDx, lookDz)
        return (totalSlope / totalWeight * minOf(lookDistance, PITCH_HORIZONTAL_CAP) * PITCH_SLOPE_SCALE)
            .coerceIn(-PITCH_CLAMP, PITCH_CLAMP)
    }

    fun ledgeNeedsBridge(player: LocalPlayer, dx: Double, dz: Double, distance: Double, profile: JumpProfile): Boolean {
        if (!player.onGround() || distance < 0.8) return false
        val nx = dx / distance
        val nz = dz / distance
        val farX = player.x + nx * LEDGE_PROBE_FAR
        val farZ = player.z + nz * LEDGE_PROBE_FAR
        val farTop = surfaceTopNear(farX, farZ, player.y, true, profile) ?: return false
        if (farTop <= player.y + profile.stepHeight + STEP_JUMP_MARGIN || farTop > player.y + profile.maxClimb) return false
        if (!BlockCache.isBodyClearAt(farX, farTop, farZ)) return false
        val midTop = surfaceTopNear(player.x + nx * LEDGE_PROBE_NEAR, player.z + nz * LEDGE_PROBE_NEAR, player.y, false, profile)
        return midTop == null || midTop < player.y - GROUND_TOLERANCE
    }

    private fun surfaceTopNear(x: Double, z: Double, feetY: Double, allowJumpHigh: Boolean, profile: JumpProfile): Double? {
        val maxOffset = if (allowJumpHigh) ceil(profile.maxClimb).toInt() else 0
        for (offset in maxOffset downTo -2) {
            val y = floor(feetY).toInt() + offset
            val shape = BlockCache.getCollisionShape(BlockPos(floor(x).toInt(), y, floor(z).toInt()))
            if (shape.isEmpty) continue
            val top = y + shape.max(Direction.Axis.Y)
            val limit = if (allowJumpHigh) profile.maxClimb else STEP_UP_MIN_HEIGHT
            if (top in (feetY - 1.5)..(feetY + limit)) return top
        }
        return null
    }

    private fun horizontalDistance(startX: Double, startZ: Double, endX: Double, endZ: Double): Double {
        val dx = endX - startX
        val dz = endZ - startZ
        return sqrt(dx * dx + dz * dz)
    }
}
