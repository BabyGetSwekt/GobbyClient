package gobby.pathfinder

import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

internal object PathHeadObstacle {
    fun isBlocked(pos: Vec3, dx: Double, dz: Double, horizontalDistance: Double): Boolean {
        val headAbove = BlockPos(floor(pos.x).toInt(), floor(pos.y + BlockCache.PLAYER_HEIGHT).toInt(), floor(pos.z).toInt())
        if (!BlockCache.isPassable(headAbove)) return true
        if (horizontalDistance < 0.05) return false
        val nx = dx / horizontalDistance
        val nz = dz / horizontalDistance
        val headAhead = BlockPos(
            floor(pos.x + nx * AHEAD_HEAD_PROBE_DIST).toInt(),
            floor(pos.y + BlockCache.PLAYER_HEIGHT - 0.3).toInt(),
            floor(pos.z + nz * AHEAD_HEAD_PROBE_DIST).toInt()
        )
        return !BlockCache.isPassable(headAhead)
    }
}
