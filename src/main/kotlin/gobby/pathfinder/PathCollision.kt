package gobby.pathfinder

import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

internal object PathCollision {
    fun isSegmentStillClear(from: Vec3, to: Vec3): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 0.5) return true
        val steps = max(2, ceil(dist / SEGMENT_CLEAR_STEP).toInt())
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val sample = samplePoint(from, dx, dy, dz, t)
            if (!isBodyClearAt(sample.x, sample.y, sample.z)) return false
        }
        return true
    }

    fun quickLineOfSight(from: Vec3, to: Vec3): Boolean {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 0.5) return true

        val perpX = -dz / dist
        val perpZ = dx / dist
        val steps = ceil(dist / LOS_STEP).toInt()
        val dy = to.y - from.y
        val offsets = doubleArrayOf(0.0, -LOS_HALF_WIDTH, LOS_HALF_WIDTH)

        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val sample = samplePoint(from, dx, dy, dz, t)
            val groundY = findGroundY(sample.x, sample.y, sample.z) ?: return false
            for (offset in offsets) {
                if (!isBodyClearAt(sample.x + perpX * offset, groundY, sample.z + perpZ * offset)) return false
            }
        }
        return true
    }

    private fun samplePoint(from: Vec3, dx: Double, dy: Double, dz: Double, t: Double): Vec3 {
        return Vec3(from.x + dx * t, from.y + dy * t, from.z + dz * t)
    }

    private fun findGroundY(x: Double, approxY: Double, z: Double): Double? {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val cursor = BlockPos.MutableBlockPos()
        for (yOff in 2 downTo -2) {
            val by = floor(approxY - 0.05 + yOff).toInt()
            val shape = collisionShapeAt(cursor, bx, by, bz)
            if (shape.isEmpty) continue
            val top = by + shape.max(Direction.Axis.Y)
            if (top in (approxY - 2.0)..(approxY + 1.5)) return top
        }
        return null
    }

    private fun isBodyClearAt(x: Double, feetY: Double, z: Double): Boolean {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val minBlockY = floor(feetY).toInt()
        val maxBlockY = floor(feetY + BlockCache.PLAYER_HEIGHT).toInt()
        val cursor = BlockPos.MutableBlockPos()
        for (by in minBlockY..maxBlockY) {
            val shape = collisionShapeAt(cursor, bx, by, bz)
            if (shape.isEmpty) continue
            val bottom = by + shape.min(Direction.Axis.Y)
            val top = by + shape.max(Direction.Axis.Y)
            if (top > feetY + GROUND_TOLERANCE && bottom < feetY + BlockCache.PLAYER_HEIGHT) return false
        }
        return true
    }

    private fun collisionShapeAt(cursor: BlockPos.MutableBlockPos, x: Int, y: Int, z: Int) =
        BlockCache.getCollisionShape(cursor.set(x, y, z))
}

internal object PathProgress {
    fun advanceCursor(cursor: Int, waypoints: List<Vec3>, pos: Vec3, isSky: Boolean, onGround: Boolean): Int {
        var nextCursor = cursor
        val yReach = if (isSky) Y_REACH_SKY else Y_REACH_GROUND
        while (nextCursor < waypoints.size) {
            val target = waypoints[nextCursor]
            val reached = withinReach(target, pos, isSky, yReach) ||
                crossedSegmentPlane(waypoints, nextCursor, target, pos)
            if (reached) nextCursor++ else break
        }
        if (!isSky && !onGround) nextCursor = skipMissedWaypointsWhileFalling(nextCursor, waypoints, pos)
        return nextCursor
    }

    private fun skipMissedWaypointsWhileFalling(cursor: Int, waypoints: List<Vec3>, pos: Vec3): Int {
        var nextCursor = cursor
        while (nextCursor < waypoints.size - 1) {
            val cur = waypoints[nextCursor]
            if (pos.y >= cur.y - FALL_SKIP_Y_TOLERANCE) break
            val next = waypoints[nextCursor + 1]
            if (next.y <= pos.y + 0.5) nextCursor++ else break
        }
        return nextCursor
    }

    private fun withinReach(target: Vec3, pos: Vec3, isSky: Boolean, yReach: Double): Boolean {
        val dx = pos.x - target.x
        val dz = pos.z - target.z
        val planarDist = sqrt(dx * dx + dz * dz)
        return if (isSky) {
            val dy = pos.y - target.y
            sqrt(planarDist * planarDist + dy * dy) < WAYPOINT_REACH
        } else {
            planarDist < WAYPOINT_REACH && abs(pos.y - target.y) < yReach
        }
    }

    private fun crossedSegmentPlane(waypoints: List<Vec3>, index: Int, target: Vec3, pos: Vec3): Boolean {
        val direction = segmentDirection(waypoints, index, target) ?: return false
        val segmentLen = sqrt(direction.x * direction.x + direction.z * direction.z)
        if (segmentLen == 0.0) return false

        val dirX = direction.x / segmentLen
        val dirZ = direction.z / segmentLen
        val toPlayerX = pos.x - target.x
        val toPlayerZ = pos.z - target.z
        val alongAxis = toPlayerX * dirX + toPlayerZ * dirZ
        val lateralAxis = abs(toPlayerX * -dirZ + toPlayerZ * dirX)
        val lateralBudget = (segmentLen * LATERAL_DRIFT_FACTOR).coerceIn(LATERAL_DRIFT_MIN, LATERAL_DRIFT_MAX)
        return alongAxis > PLANE_TOLERANCE && lateralAxis < lateralBudget
    }

    private fun segmentDirection(waypoints: List<Vec3>, index: Int, target: Vec3): Vec3? {
        if (index < waypoints.lastIndex) {
            val next = waypoints[index + 1]
            return Vec3(next.x - target.x, 0.0, next.z - target.z)
        }
        if (index > 0) {
            val prev = waypoints[index - 1]
            return Vec3(target.x - prev.x, 0.0, target.z - prev.z)
        }
        return null
    }
}
