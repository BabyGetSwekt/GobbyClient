package gobby.pathfinder

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

object PathFollowMath {
    private const val PREDICT_BASE_TICKS = 2.0
    private const val PREDICT_MAX_TICKS = 6.0
    private const val PREDICT_SPEED_TICK_DIVISOR = 100.0
    private const val PREDICT_MIN_DISTANCE = 0.15
    private const val PREDICT_MAX_DISTANCE = 2.6
    private const val PREDICT_DIST_BASE = 0.8
    private const val PREDICT_DIST_SPEED_DIVISOR = 250.0

    data class SegmentDeviation(
        val lateral: Double,
        val verticalBelow: Double,
        val segmentDy: Double
    )

    data class GroundSegmentFrame(
        val dirX: Double,
        val dirZ: Double,
        val perpX: Double,
        val perpZ: Double,
        val lateral: Double,
        val distanceToTarget: Double,
        val segmentDy: Double
    )

    fun predictedMovementPos(pos: Vec3, player: LocalPlayer, velX: Double, velZ: Double): Vec3 {
        val horizontalSpeed = sqrt(velX * velX + velZ * velZ)
        if (horizontalSpeed < PREDICT_MIN_DISTANCE / PREDICT_MAX_TICKS) return pos

        val skyblockSpeed = player.abilities.walkingSpeed * 1000.0
        val ticksAhead = (PREDICT_BASE_TICKS + skyblockSpeed / PREDICT_SPEED_TICK_DIVISOR)
            .coerceIn(PREDICT_BASE_TICKS, PREDICT_MAX_TICKS)
        val projectedDistance = horizontalSpeed * ticksAhead
        val maxDistance = (PREDICT_DIST_BASE + skyblockSpeed / PREDICT_DIST_SPEED_DIVISOR).coerceIn(1.0, PREDICT_MAX_DISTANCE)
        val scale = if (projectedDistance > maxDistance) maxDistance / projectedDistance else 1.0

        return Vec3(
            pos.x + velX * ticksAhead * scale,
            pos.y + player.deltaMovement.y * ticksAhead * scale,
            pos.z + velZ * ticksAhead * scale
        )
    }

    fun segmentDeviation(waypoints: List<Vec3>, idx: Int, pos: Vec3): SegmentDeviation? {
        if (idx <= 0 || idx >= waypoints.size) return null
        return segmentDeviationBetween(waypoints[idx - 1], waypoints[idx], pos)
    }

    fun routeDeviation(waypoints: List<Vec3>, cursor: Int, pos: Vec3, behind: Int, ahead: Int): SegmentDeviation? {
        if (waypoints.size < 2) return null
        val first = (cursor - behind).coerceAtLeast(1)
        val last = (cursor + ahead).coerceAtMost(waypoints.lastIndex)
        var best: SegmentDeviation? = null
        var bestScore = Double.MAX_VALUE
        for (idx in first..last) {
            val deviation = segmentDeviationBetween(waypoints[idx - 1], waypoints[idx], pos) ?: continue
            val score = deviation.lateral + abs(deviation.verticalBelow) * ROUTE_DEVIATION_Y_WEIGHT
            if (score < bestScore) {
                bestScore = score
                best = deviation
            }
        }
        return best
    }

    private fun segmentDeviationBetween(prev: Vec3, target: Vec3, pos: Vec3): SegmentDeviation? {
        val segDx = target.x - prev.x
        val segDz = target.z - prev.z
        val segLen = sqrt(segDx * segDx + segDz * segDz)
        if (segLen < 0.01) return null

        val dirX = segDx / segLen
        val dirZ = segDz / segLen
        val relX = pos.x - prev.x
        val relZ = pos.z - prev.z
        val along = (relX * dirX + relZ * dirZ).coerceIn(0.0, segLen)
        val t = along / segLen
        val pathY = prev.y + (target.y - prev.y) * t
        val lateral = abs(relX * -dirZ + relZ * dirX)
        return SegmentDeviation(lateral, pathY - pos.y, target.y - prev.y)
    }

    fun groundSegmentFrame(waypoints: List<Vec3>, idx: Int, pos: Vec3): GroundSegmentFrame {
        val target = waypoints[idx]
        if (idx <= 0) return directFrame(pos, target, target.y - pos.y)

        val prev = waypoints[idx - 1]
        val segDx = target.x - prev.x
        val segDz = target.z - prev.z
        val segLen = sqrt(segDx * segDx + segDz * segDz)
        if (segLen < 0.01) return directFrame(pos, target, target.y - prev.y)

        val dirX = segDx / segLen
        val dirZ = segDz / segLen
        val perpX = -dirZ
        val perpZ = dirX
        val relX = pos.x - prev.x
        val relZ = pos.z - prev.z
        val playerAlong = (relX * dirX + relZ * dirZ).coerceIn(0.0, segLen)
        val playerLateral = relX * perpX + relZ * perpZ
        return GroundSegmentFrame(dirX, dirZ, perpX, perpZ, playerLateral, segLen - playerAlong, target.y - prev.y)
    }

    fun dampedSteerDirection(frame: GroundSegmentFrame, lateralVel: Double, lookDx: Double, lookDz: Double): Pair<Double, Double> {
        val len = sqrt(lookDx * lookDx + lookDz * lookDz)
        if (len < 0.01) return frame.dirX to frame.dirZ
        if (abs(frame.lateral) < CENTERING_DEADBAND && abs(lateralVel) < CENTERING_DEADBAND_VEL) {
            return lookDx / len to lookDz / len
        }
        val damping = (-lateralVel * CENTERING_DAMPING).coerceIn(-CENTERING_DAMP_MAX, CENTERING_DAMP_MAX)
        val x = lookDx / len + frame.perpX * damping
        val z = lookDz / len + frame.perpZ * damping
        val outLen = sqrt(x * x + z * z)
        return if (outLen < 0.01) frame.dirX to frame.dirZ else x / outLen to z / outLen
    }

    fun centered(vec: Vec3): Vec3 {
        return Vec3(floor(vec.x) + 0.5, vec.y, floor(vec.z) + 0.5)
    }

    fun lookaheadDistanceFor(speedPerTick: Double): Double =
        (PATH_LOOKAHEAD_MIN + speedPerTick * PATH_LOOKAHEAD_RESPONSE_TICKS)
            .coerceIn(PATH_LOOKAHEAD_MIN, PATH_LOOKAHEAD_MAX)

    fun pathLookaheadTarget(waypoints: List<Vec3>, idx: Int, pos: Vec3, lookaheadDistance: Double): Vec3 {
        if (waypoints.isEmpty()) return pos
        if (idx <= 0) return centered(waypoints[idx.coerceIn(0, waypoints.lastIndex)])

        var remainingLookahead = lookaheadDistance

        var segmentStart = centered(waypoints[idx - 1])
        var segmentEnd = centered(waypoints[idx])
        val currentProjection = projectOntoSegment(segmentStart, segmentEnd, pos)
        val distanceToSegmentEnd = currentProjection.distanceTo(segmentEnd)

        if (remainingLookahead <= distanceToSegmentEnd || idx >= waypoints.lastIndex) {
            return moveAlongSegment(currentProjection, segmentEnd, remainingLookahead)
        }

        remainingLookahead -= distanceToSegmentEnd
        var nextIndex = idx + 1
        segmentStart = segmentEnd
        while (nextIndex <= waypoints.lastIndex) {
            segmentEnd = centered(waypoints[nextIndex])
            val segmentLength = segmentStart.distanceTo(segmentEnd)
            if (segmentLength >= remainingLookahead || nextIndex == waypoints.lastIndex) {
                return moveAlongSegment(segmentStart, segmentEnd, remainingLookahead)
            }
            remainingLookahead -= segmentLength
            segmentStart = segmentEnd
            nextIndex++
        }

        return centered(waypoints.last())
    }

    fun upcomingTurnDegrees(waypoints: List<Vec3>, idx: Int, pos: Vec3, withinDist: Double): Double {
        var maxTurn = 0.0
        var accDist = pos.distanceTo(waypoints[idx.coerceIn(0, waypoints.lastIndex)])
        for (i in maxOf(idx, 1) until waypoints.lastIndex) {
            if (accDist >= withinDist) break
            val into = headingDegrees(waypoints[i - 1], waypoints[i])
            val outOf = headingDegrees(waypoints[i], waypoints[i + 1])
            if (into != null && outOf != null) {
                val turn = abs(wrapDeg(outOf - into))
                if (turn > maxTurn) maxTurn = turn
            }
            accDist += waypoints[i].distanceTo(waypoints[i + 1])
        }
        return maxTurn
    }

    private fun headingDegrees(from: Vec3, to: Vec3): Double? {
        val dx = to.x - from.x
        val dz = to.z - from.z
        if (dx * dx + dz * dz < 0.0001) return null
        return Math.toDegrees(atan2(dz, dx))
    }

    private fun wrapDeg(deg: Double): Double {
        var d = deg % 360.0
        if (d >= 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }

    private fun directFrame(pos: Vec3, target: Vec3, dy: Double): GroundSegmentFrame {
        val dx = target.x - pos.x
        val dz = target.z - pos.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 0.01) return GroundSegmentFrame(0.0, 1.0, -1.0, 0.0, 0.0, dist, dy)
        val dirX = dx / dist
        val dirZ = dz / dist
        return GroundSegmentFrame(dirX, dirZ, -dirZ, dirX, 0.0, dist, dy)
    }

    private fun projectOntoSegment(start: Vec3, end: Vec3, pos: Vec3): Vec3 {
        val dx = end.x - start.x
        val dz = end.z - start.z
        val lenSq = dx * dx + dz * dz
        if (lenSq < 0.0001) return end
        val t = (((pos.x - start.x) * dx + (pos.z - start.z) * dz) / lenSq).coerceIn(0.0, 1.0)
        return Vec3(start.x + dx * t, start.y + (end.y - start.y) * t, start.z + dz * t)
    }

    private fun moveAlongSegment(start: Vec3, end: Vec3, distance: Double): Vec3 {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val len = sqrt(dx * dx + dz * dz)
        if (len < 0.01) return end
        val t = (distance / len).coerceIn(0.0, 1.0)
        return Vec3(start.x + dx * t, start.y + dy * t, start.z + dz * t)
    }
}
