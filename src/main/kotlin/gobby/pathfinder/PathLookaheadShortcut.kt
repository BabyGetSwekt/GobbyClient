package gobby.pathfinder

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.min

internal object PathLookaheadShortcut {
    fun find(waypoints: List<Vec3>, cursor: Int, pos: Vec3, player: LocalPlayer, cooldown: Int): Pair<Int, Int> {
        if (!player.onGround()) return cursor to cooldown
        val nextCooldown = cooldown - 1
        if (nextCooldown > 0) return cursor to nextCooldown
        val ceiling = min(cursor + LOOK_AHEAD_MAX_INDEX_DELTA, waypoints.lastIndex)
        for (index in ceiling downTo cursor + 1) {
            val waypoint = waypoints[index]
            if (!withinRange(pos, waypoint)) continue
            if (PathCollision.quickLineOfSight(pos, waypoint)) return index to LOOK_AHEAD_INTERVAL
        }
        return cursor to LOOK_AHEAD_INTERVAL
    }

    private fun withinRange(pos: Vec3, waypoint: Vec3): Boolean {
        val dx = pos.x - waypoint.x
        val dy = pos.y - waypoint.y
        val dz = pos.z - waypoint.z
        return dx * dx + dy * dy + dz * dz <= LOOK_AHEAD_RANGE_SQ && waypoint.y - pos.y <= LOOK_AHEAD_MAX_CLIMB
    }
}
