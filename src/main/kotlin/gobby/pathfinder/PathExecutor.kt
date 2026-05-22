package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.pathfinder.world.BlockCache
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PlayerUtils
import gobby.utils.rotation.AngleUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

object PathExecutor {

    private const val WAYPOINT_REACH = 0.8
    private const val Y_REACH_GROUND = 2.0
    private const val Y_REACH_SKY = 0.6
    private const val LATERAL_DRIFT_MIN = 1.5
    private const val LATERAL_DRIFT_MAX = 4.0
    private const val LATERAL_DRIFT_FACTOR = 0.4
    private const val PLANE_TOLERANCE = -0.1
    private const val ANTI_SPIN_DIST = 0.5
    private const val STRAFE_YAW_MIN = 50f
    private const val BACKWARD_YAW = 145f
    private const val BACKWARD_NO_STRAFE_YAW = 170f
    private const val SPEED_SPRINT_YAW = 25f
    private const val SPEED_JOG_YAW = 55f
    private const val SPEED_WALK_YAW = 90f
    private const val TANGENT_MAX_SEGMENTS = 10
    private const val TANGENT_MAX_DISTANCE = 7.0
    private const val TANGENT_CURRENT_WEIGHT_MULT = 0.0
    private const val JUMP_LOOKAHEAD_WAYPOINTS = 2
    private const val JUMP_LOOKAHEAD_DIST = 3.0
    private const val PITCH_MAX_SEGMENTS = 12
    private const val PITCH_MAX_DISTANCE = 12.0
    private const val PITCH_SLOPE_SCALE = 0.4
    private const val PITCH_CLAMP = 3.0
    private const val PITCH_HORIZONTAL_CAP = 8.0
    private const val PRE_TURN_THRESHOLD = 25f
    private const val PRE_TURN_BLEND_MAX = 0.08
    private const val PRE_TURN_BLEND_RANGE = 4.0
    private const val LOOK_DIST_MIN = 5.0
    private const val JUMP_HOLD_THRESHOLD = 0.35
    private const val SNEAK_HOLD_THRESHOLD = 0.35
    private const val YAW_SLEW_NEAR = 0.18f
    private const val YAW_SLEW_FAR = 0.45f
    private const val YAW_SLEW_FAR_THRESHOLD = 45f
    private const val PITCH_SLEW = 0.15f
    private const val REPLAN_TICKS = 80
    private const val MICRO_PAUSE_CHANCE = 0.012
    private const val MICRO_PAUSE_MIN_TICKS = 1
    private const val MICRO_PAUSE_MAX_TICKS = 4
    private const val MICRO_PAUSE_COOLDOWN = 60
    private const val STUCK_STRAFE_THRESHOLD = 40
    private const val STUCK_REPATH_THRESHOLD = 100
    private const val STUCK_MOVE_EPSILON_SQ = 0.04
    private const val STRAFE_BACKWARD_TICKS = 15
    private const val STRAFE_WIGGLE_PERIOD = 10
    private const val STUCK_BLACKLIST_RADIUS = 3.5
    private const val STUCK_BLACKLIST_WAYPOINT_RADIUS = 2.5
    private const val STUCK_BLACKLIST_DURATION = 600L
    private const val LOOK_AHEAD_INTERVAL = 10
    private const val LOOK_AHEAD_RANGE_SQ = 900.0
    private const val LOOK_AHEAD_MAX_INDEX_DELTA = 15
    private const val LOOK_AHEAD_MAX_CLIMB = 1.0
    private const val DYNAMIC_REPATH_INTERVAL = 20
    private const val DYNAMIC_REPATH_LOOKAHEAD = 4
    private const val OBSTRUCTION_CONFIRM_HITS = 3
    private const val OBSTRUCTION_GRACE_TICKS = 40
    private const val OFF_PATH_DIST_SQ = 25.0
    private const val OFF_PATH_Y_DIFF = 4.0
    private const val OFF_PATH_CONFIRM_TICKS = 30
    private const val AHEAD_HEAD_PROBE_DIST = 1.2
    private const val FALL_SKIP_Y_TOLERANCE = 1.0
    private const val GOAL_ARRIVE_DIST_SQ = 4.0
    private const val GOAL_ARRIVE_Y_DIFF = 2.5
    private const val SEGMENT_CLEAR_STEP = 0.8
    private const val SEGMENT_PROGRESS_THRESHOLD = 0.70
    private const val DEFAULT_SEGMENT_SIZE = 50
    private const val LOS_HALF_WIDTH = BlockCache.PLAYER_HALF_WIDTH
    private const val LOS_STEP = 0.3
    private const val DROP_LOOK_PITCH = 60f
    private val PROBE_DISTANCES = doubleArrayOf(0.4, 0.8, 1.3)
    private const val STEP_UP_MIN_HEIGHT = 0.6
    private const val STEP_UP_MAX_HEIGHT = BlockCache.MAX_JUMP_RISE
    private const val PROBE_DEPTH_BELOW = -0.5
    private const val LEDGE_PROBE_NEAR = 0.7
    private const val LEDGE_PROBE_FAR = 1.5
    private const val GROUND_TOLERANCE = 0.01

    var enableMicroPauses: Boolean = true
    var enableSpeedAdaptation: Boolean = true
    var enableSprint: Boolean = true
    var enableSegmented: Boolean = false
    var segmentBlocks: Int = DEFAULT_SEGMENT_SIZE

    private var plan: RoutePlan = RoutePlan.Failed
    private var cursor: Int = 0
    private var desiredYaw: Float = Float.NaN
    private var desiredPitch: Float = Float.NaN
    private var lastCursor: Int = 0
    private var microPauseRemaining: Int = 0
    private var microPauseCooldown: Int = 0
    private var noProgressTicks: Int = 0
    private var positionStuckTicks: Int = 0
    private var lastPosForStuck: Vec3? = null
    private var inRecovery: Boolean = false
    private var recoveryTicks: Int = 0
    private var recoveryStrafeRight: Boolean = true
    private var lookAheadCooldown: Int = 0
    private var dynamicRepathCooldown: Int = 0
    private var obstructionHits: Int = 0
    private var graceTicksRemaining: Int = 0
    private var offPathTicks: Int = 0
    private var repathInFlight: Boolean = false
    private var finalGoal: Vec3? = null
    private var travelMode: TravelMode = TravelMode.WALK
    private var segmentInFlight: Boolean = false
    private var segmentedRoute: Boolean = false
    private var waypointsMutable: MutableList<Vec3> = mutableListOf()

    fun beginLongPath(start: Vec3, finalGoal: Vec3, mode: TravelMode = TravelMode.WALK) {
        enableSegmented = false
        planFullAsync(start, finalGoal, mode)
    }

    private fun planFullAsync(start: Vec3, finalGoal: Vec3, mode: TravelMode) {
        RouteEngine.planAsync(start, finalGoal, mode).thenAccept { plan ->
            mc.execute {
                if (plan is RoutePlan.Failed) {
                    modMessage("§cNo route found in ${PlanStats.lastTotalMs}ms (${PlanStats.lastPolygonCount} polys scanned).")
                } else {
                    reportTimings(plan, segmented = false)
                    begin(plan, finalGoal, mode)
                }
            }
        }
    }

    private fun reportTimings(plan: RoutePlan, segmented: Boolean) {
        val suffix = if (segmented) " (segment of ${plan.waypoints.size} waypoints)" else " (${plan.waypoints.size} waypoints)"
        val pieces = buildList {
            add("§atotal=${PlanStats.lastTotalMs}ms")
            if (PlanStats.lastMeshMs > 0) add("§7mesh=${PlanStats.lastMeshMs}ms (${PlanStats.lastPolygonCount} polys)")
            if (PlanStats.lastSolveMs > 0) add("§7solve=${PlanStats.lastSolveMs}ms")
        }
        modMessage("§aRoute found$suffix § — ${pieces.joinToString(" §8| ")}§a.")
    }

    fun begin(routePlan: RoutePlan, originalGoal: Vec3? = null, mode: TravelMode = TravelMode.WALK) {
        if (routePlan.isEmpty) {
            modMessage("§cNo route to follow.")
            return
        }
        plan = routePlan
        waypointsMutable = routePlan.waypoints.toMutableList()
        cursor = 1
        desiredYaw = Float.NaN
        desiredPitch = Float.NaN
        lastCursor = 0
        microPauseRemaining = 0
        microPauseCooldown = MICRO_PAUSE_COOLDOWN
        noProgressTicks = 0
        positionStuckTicks = 0
        lastPosForStuck = null
        inRecovery = false
        recoveryTicks = 0
        lookAheadCooldown = LOOK_AHEAD_INTERVAL
        dynamicRepathCooldown = DYNAMIC_REPATH_INTERVAL
        obstructionHits = 0
        graceTicksRemaining = OBSTRUCTION_GRACE_TICKS
        offPathTicks = 0
        repathInFlight = false
        segmentInFlight = false
        finalGoal = originalGoal ?: routePlan.waypoints.last()
        travelMode = mode
        segmentedRoute = enableSegmented && originalGoal != null &&
                originalGoal.distanceTo(routePlan.waypoints.last()) > 1.5
    }

    fun stop() {
        if (plan === RoutePlan.Failed) return
        plan = RoutePlan.Failed
        cursor = 0
        desiredYaw = Float.NaN
        desiredPitch = Float.NaN
        inRecovery = false
        repathInFlight = false
        finalGoal = null
        InputManager.releaseAll()
    }

    fun running(): Boolean = plan !is RoutePlan.Failed

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        if (running()) stop()
    }

    fun currentWaypoints(): List<Vec3> = waypointsMutable.toList()

    fun currentCursor(): Int = cursor

    fun isSkyPath(): Boolean = plan is RoutePlan.Sky

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        PathBlacklist.tick()
        val active = plan
        if (active is RoutePlan.Failed) return
        val player = mc.player ?: return

        val waypoints = waypointsMutable
        if (cursor >= waypoints.size) {
            arrive()
            return
        }

        val isSky = active is RoutePlan.Sky
        val pos = PlayerUtils.getSyncedPos() ?: player.position()
        advanceCursor(waypoints, pos, isSky)
        if (cursor >= waypoints.size) {
            arrive()
            return
        }

        if (segmentedRoute && !segmentInFlight && shouldRequestNextSegment(waypoints)) {
            requestNextSegment()
        }

        trackStuckness(pos)
        if (repathInFlight) return
        if (applyRecoveryInputsIfNeeded(player)) return

        if (microPauseTick()) return

        if (!isSky && dynamicRepathDueAndBlocked(waypoints, pos)) {
            triggerObstructionRepath(pos)
            return
        }

        if (!isSky) tryLookAheadShortcut(waypoints, pos, player)
        if (cursor >= waypoints.size) { arrive(); return }

        if (isSky) steerSky(waypoints[cursor], pos, player)
        else steerGround(waypoints, cursor, pos, player)
    }

    private fun dynamicRepathDueAndBlocked(waypoints: List<Vec3>, pos: Vec3): Boolean {
        if (graceTicksRemaining > 0) { graceTicksRemaining--; return false }
        dynamicRepathCooldown--
        if (dynamicRepathCooldown > 0) return false
        dynamicRepathCooldown = DYNAMIC_REPATH_INTERVAL
        val ceiling = min(cursor + DYNAMIC_REPATH_LOOKAHEAD, waypoints.lastIndex)
        var blocked = false
        for (i in cursor until ceiling) {
            val from = if (i == cursor) pos else waypoints[i]
            if (!isSegmentStillClear(from, waypoints[i + 1])) { blocked = true; break }
        }
        if (!blocked) { obstructionHits = 0; return false }
        obstructionHits++
        return obstructionHits >= OBSTRUCTION_CONFIRM_HITS
    }

    private fun isSegmentStillClear(from: Vec3, to: Vec3): Boolean {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 0.5) return true
        val steps = max(2, ceil(dist / SEGMENT_CLEAR_STEP).toInt())
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val sx = from.x + dx * t
            val sy = from.y + dy * t
            val sz = from.z + dz * t
            if (!isBodyClearAt(sx, sy, sz)) return false
        }
        return true
    }

    private fun skipToNearestReachableWaypoint(pos: Vec3): Boolean {
        val waypoints = plan.waypoints
        if (cursor >= waypoints.size) return false
        val maxLook = min(cursor + 6, waypoints.lastIndex)
        var bestIdx = -1
        var bestDistSq = OFF_PATH_DIST_SQ
        for (i in (cursor + 1)..maxLook) {
            val wp = waypoints[i]
            val dx = pos.x - wp.x
            val dz = pos.z - wp.z
            val dy = abs(pos.y - wp.y)
            if (dy > OFF_PATH_Y_DIFF) continue
            val d = dx * dx + dz * dz
            if (d < bestDistSq) { bestDistSq = d; bestIdx = i }
        }
        if (bestIdx > cursor) { cursor = bestIdx; return true }
        return false
    }

    private fun isOffPath(pos: Vec3): Boolean {
        if (mc.player?.onGround() == false) return false
        val waypoints = plan.waypoints
        if (cursor >= waypoints.size) return false
        val tgt = waypoints[cursor]
        val dx = pos.x - tgt.x
        val dz = pos.z - tgt.z
        val dy = pos.y - tgt.y
        return (dx * dx + dz * dz) > OFF_PATH_DIST_SQ || abs(dy) > OFF_PATH_Y_DIFF
    }

    private fun triggerOffPathRepath(pos: Vec3) {
        val goal = finalGoal ?: return
        repathInFlight = true
        inRecovery = false
        InputManager.releaseAll()
        modMessage("§eOff-path detected — recalculating...")
        RouteEngine.planAsync(pos, goal, travelMode).thenAccept { newPlan ->
            mc.execute {
                if (newPlan is RoutePlan.Failed) {
                    modMessage("§cReroute failed.")
                    stop()
                } else {
                    begin(newPlan, goal, travelMode)
                }
            }
        }
    }

    private fun triggerObstructionRepath(pos: Vec3) {
        val goal = finalGoal ?: return
        if (repathInFlight) return
        repathInFlight = true
        InputManager.releaseAll()
        modMessage("§eRoute obstructed, recalculating...")
        RouteEngine.planAsync(pos, goal, travelMode).thenAccept { newPlan ->
            mc.execute {
                if (newPlan is RoutePlan.Failed) {
                    modMessage("§cReroute failed.")
                    stop()
                } else {
                    begin(newPlan, goal, travelMode)
                }
            }
        }
    }

    private fun trackStuckness(pos: Vec3) {
        if (!repathInFlight) {
            if (isOffPath(pos)) {
                if (skipToNearestReachableWaypoint(pos)) {
                    offPathTicks = 0
                } else {
                    offPathTicks++
                    if (offPathTicks >= OFF_PATH_CONFIRM_TICKS) {
                        offPathTicks = 0
                        triggerOffPathRepath(pos)
                        return
                    }
                }
            } else {
                offPathTicks = 0
            }
        }
        if (cursor != lastCursor) {
            lastCursor = cursor
            noProgressTicks = 0
            positionStuckTicks = 0
            lastPosForStuck = null
            if (inRecovery) {
                inRecovery = false
                recoveryTicks = 0
                InputManager.release(MoveAction.LEFT)
                InputManager.release(MoveAction.RIGHT)
                InputManager.release(MoveAction.BACKWARD)
            }
            return
        }
        noProgressTicks++
        val last = lastPosForStuck
        if (last != null) {
            val dx = pos.x - last.x
            val dz = pos.z - last.z
            val movedSq = dx * dx + dz * dz
            if (movedSq < STUCK_MOVE_EPSILON_SQ) positionStuckTicks++
            else positionStuckTicks = max(0, positionStuckTicks - 2)
        }
        lastPosForStuck = pos

        val nowStuck = noProgressTicks >= STUCK_STRAFE_THRESHOLD && positionStuckTicks >= STUCK_STRAFE_THRESHOLD / 2
        val nowVeryStuck = noProgressTicks >= STUCK_REPATH_THRESHOLD && positionStuckTicks >= STUCK_REPATH_THRESHOLD / 2

        if (nowVeryStuck && !repathInFlight) {
            triggerStuckRepath(pos)
            return
        }
        if (nowStuck && !inRecovery) {
            inRecovery = true
            recoveryTicks = 0
            recoveryStrafeRight = kotlin.random.Random.nextBoolean()
        }
    }

    private fun applyRecoveryInputsIfNeeded(player: LocalPlayer): Boolean {
        if (!inRecovery) return false
        InputManager.releaseAll()
        recoveryTicks++
        if (recoveryTicks <= STRAFE_BACKWARD_TICKS) {
            InputManager.press(MoveAction.BACKWARD)
            if (player.onGround()) InputManager.press(MoveAction.JUMP)
        } else {
            val phase = recoveryTicks - STRAFE_BACKWARD_TICKS
            if (phase % STRAFE_WIGGLE_PERIOD == 0) recoveryStrafeRight = !recoveryStrafeRight
            if (recoveryStrafeRight) InputManager.press(MoveAction.RIGHT) else InputManager.press(MoveAction.LEFT)
            InputManager.press(MoveAction.FORWARD)
            if (player.onGround()) InputManager.press(MoveAction.JUMP)
        }
        return true
    }

    private fun triggerStuckRepath(pos: Vec3) {
        val goal = finalGoal ?: return
        repathInFlight = true
        inRecovery = false
        InputManager.releaseAll()
        modMessage("§cStuck! Blacklisting and replanning...")
        PathBlacklist.blacklistArea(pos, STUCK_BLACKLIST_RADIUS, STUCK_BLACKLIST_DURATION)
        plan.waypoints.getOrNull(cursor)?.let {
            PathBlacklist.blacklistArea(it, STUCK_BLACKLIST_WAYPOINT_RADIUS, STUCK_BLACKLIST_DURATION)
        }
        RouteEngine.planAsync(pos, goal, travelMode).thenAccept { newPlan ->
            mc.execute {
                if (newPlan is RoutePlan.Failed) {
                    modMessage("§cRepath failed.")
                    stop()
                } else {
                    modMessage("§aNew route found (${newPlan.waypoints.size} waypoints).")
                    begin(newPlan, goal, travelMode)
                }
            }
        }
    }

    private fun tryLookAheadShortcut(waypoints: List<Vec3>, pos: Vec3, player: LocalPlayer) {
        if (!player.onGround()) return
        lookAheadCooldown--
        if (lookAheadCooldown > 0) return
        lookAheadCooldown = LOOK_AHEAD_INTERVAL
        val ceiling = min(cursor + LOOK_AHEAD_MAX_INDEX_DELTA, waypoints.lastIndex)
        for (i in ceiling downTo cursor + 1) {
            val wp = waypoints[i]
            val dx = pos.x - wp.x
            val dy = pos.y - wp.y
            val dz = pos.z - wp.z
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > LOOK_AHEAD_RANGE_SQ) continue
            if (wp.y - pos.y > LOOK_AHEAD_MAX_CLIMB) continue
            if (quickLineOfSight(pos, wp)) {
                cursor = i
                break
            }
        }
    }

    private fun quickLineOfSight(from: Vec3, to: Vec3): Boolean {
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
            val sx = from.x + dx * t
            val sy = from.y + dy * t
            val sz = from.z + dz * t
            val groundY = findGroundY(sx, sy, sz) ?: return false
            for (offset in offsets) {
                val px = sx + perpX * offset
                val pz = sz + perpZ * offset
                if (!isBodyClearAt(px, groundY, pz)) return false
            }
        }
        return true
    }

    private fun findGroundY(x: Double, approxY: Double, z: Double): Double? {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val cursor = BlockPos.MutableBlockPos()
        for (yOff in 2 downTo -2) {
            val by = floor(approxY - 0.05 + yOff).toInt()
            cursor.set(bx, by, bz)
            val shape = BlockCache.getCollisionShape(cursor)
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
            cursor.set(bx, by, bz)
            val shape = BlockCache.getCollisionShape(cursor)
            if (shape.isEmpty) continue
            val bottom = by + shape.min(Direction.Axis.Y)
            val top = by + shape.max(Direction.Axis.Y)
            if (top > feetY + GROUND_TOLERANCE && bottom < feetY + BlockCache.PLAYER_HEIGHT) return false
        }
        return true
    }

    private fun arrive() {
        val pos = mc.player?.position()
        val goal = finalGoal
        if (pos != null && goal != null) {
            val dx = pos.x - goal.x
            val dz = pos.z - goal.z
            val planar = dx * dx + dz * dz
            val dy = abs(pos.y - goal.y)
            if (planar > GOAL_ARRIVE_DIST_SQ || dy > GOAL_ARRIVE_Y_DIFF) {
                if (!repathInFlight) {
                    modMessage("§eReached end of partial path — replanning to reach goal...")
                    triggerOffPathRepath(pos)
                }
                return
            }
        }
        modMessage("§aRoute complete!")
        stop()
    }

    private fun shouldRequestNextSegment(waypoints: List<Vec3>): Boolean {
        if (waypoints.isEmpty()) return false
        return cursor.toDouble() / waypoints.size >= SEGMENT_PROGRESS_THRESHOLD
    }

    private fun requestNextSegment() {
        val goal = finalGoal ?: return
        val waypoints = waypointsMutable
        if (waypoints.isEmpty()) return
        val segStart = waypoints.last()
        segmentInFlight = true
        RouteEngine.planSegmentAsync(segStart, goal, segmentBlocks, travelMode).thenAccept { result ->
            mc.execute { handleSegmentResult(result) }
        }
    }

    private fun handleSegmentResult(result: RouteEngine.SegmentResult) {
        segmentInFlight = false
        val newPlan = result.plan
        if (newPlan is RoutePlan.Failed || newPlan.waypoints.isEmpty()) {
            modMessage(if (result.isFinal) "§cFinal segment failed near goal." else "§eSegment failed, retrying full path...")
            if (result.isFinal) { stop(); return }
            val curPos = mc.player?.position() ?: return
            triggerObstructionRepath(curPos)
            return
        }
        for (i in 1 until newPlan.waypoints.size) waypointsMutable += newPlan.waypoints[i]
        if (result.isFinal) segmentedRoute = false
    }

    private fun advanceCursor(waypoints: List<Vec3>, pos: Vec3, isSky: Boolean) {
        val yReach = if (isSky) Y_REACH_SKY else Y_REACH_GROUND
        while (cursor < waypoints.size) {
            val target = waypoints[cursor]
            val reached = withinReach(target, pos, isSky, yReach) ||
                    crossedSegmentPlane(waypoints, cursor, target, pos)
            if (reached) cursor++ else break
        }
        if (!isSky) skipMissedWaypointsWhileFalling(waypoints, pos)
    }

    private fun skipMissedWaypointsWhileFalling(waypoints: List<Vec3>, pos: Vec3) {
        if (mc.player?.onGround() == true) return
        while (cursor < waypoints.size - 1) {
            val cur = waypoints[cursor]
            if (pos.y >= cur.y - FALL_SKIP_Y_TOLERANCE) break
            val next = waypoints[cursor + 1]
            if (next.y <= pos.y + 0.5) cursor++ else break
        }
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

    private fun microPauseTick(): Boolean {
        if (!enableMicroPauses) return false
        if (microPauseRemaining > 0) {
            microPauseRemaining--
            InputManager.releaseAll()
            return true
        }
        if (microPauseCooldown > 0) {
            microPauseCooldown--
            return false
        }
        if (Random.nextDouble() < MICRO_PAUSE_CHANCE) {
            microPauseRemaining = Random.nextInt(MICRO_PAUSE_MIN_TICKS, MICRO_PAUSE_MAX_TICKS + 1)
            microPauseCooldown = MICRO_PAUSE_COOLDOWN
        }
        return false
    }

    private fun steerSky(target: Vec3, pos: Vec3, player: LocalPlayer) {
        val dx = target.x - pos.x
        val dz = target.z - pos.z
        val dy = target.y - pos.y
        val horizontalDist = sqrt(dx * dx + dz * dz)

        if (horizontalDist > 0.05) {
            val angles = AngleUtils.calcAimAnglesFromDelta(dx, dy, dz)
            desiredYaw = angles.first
            desiredPitch = angles.second
        } else if (!player.onGround() && dy < -1.0) {
            desiredPitch = DROP_LOOK_PITCH
        }

        InputManager.releaseAll()

        val headBlocked = isAheadHeadBlocked(pos, dx, dz, horizontalDist)
        if (headBlocked) {
            InputManager.press(MoveAction.SNEAK)
            InputManager.press(MoveAction.FORWARD)
            return
        }

        val yawError = if (desiredYaw.isNaN()) 0f else abs(AngleUtils.wrapDegrees(desiredYaw - player.yRot))
        if (yawError < SPEED_SPRINT_YAW || horizontalDist < ANTI_SPIN_DIST) {
            InputManager.press(MoveAction.FORWARD)
        }

        when {
            dy > JUMP_HOLD_THRESHOLD -> InputManager.press(MoveAction.JUMP)
            dy < -SNEAK_HOLD_THRESHOLD -> InputManager.press(MoveAction.SNEAK)
        }
    }

    private fun isAheadHeadBlocked(pos: Vec3, dx: Double, dz: Double, horizontalDist: Double): Boolean {
        val headAbove = BlockPos(floor(pos.x).toInt(), floor(pos.y + BlockCache.PLAYER_HEIGHT).toInt(), floor(pos.z).toInt())
        if (!BlockCache.isPassable(headAbove)) return true
        if (horizontalDist < 0.05) return false
        val nx = dx / horizontalDist
        val nz = dz / horizontalDist
        val px = pos.x + nx * AHEAD_HEAD_PROBE_DIST
        val pz = pos.z + nz * AHEAD_HEAD_PROBE_DIST
        val headAhead = BlockPos(floor(px).toInt(), floor(pos.y + BlockCache.PLAYER_HEIGHT - 0.3).toInt(), floor(pz).toInt())
        return !BlockCache.isPassable(headAhead)
    }

    private fun steerGround(waypoints: List<Vec3>, idx: Int, pos: Vec3, player: LocalPlayer) {
        val target = waypoints[idx]
        val dx = target.x - pos.x
        val dz = target.z - pos.z
        val horizontalDist = sqrt(dx * dx + dz * dz)

        InputManager.releaseAll()

        if (target.y < pos.y - 1.5) {
            steerFall(target, pos, player, dx, dz)
            return
        }

        val groundFoot = player.onGround()
        val waypointDy = if (idx > 0) target.y - waypoints[idx - 1].y else target.y - pos.y
        val needsWaypointJump = waypointDy > 0.9 && waypointDy <= STEP_UP_MAX_HEIGHT && horizontalDist < 1.5 && (target.y - pos.y) > 0.5
        val needsTerrainJump = terrainNeedsPrejump(player, dx, dz, horizontalDist) || ledgeNeedsBridge(player, dx, dz, horizontalDist)
        if ((needsWaypointJump || needsTerrainJump) && groundFoot) InputManager.press(MoveAction.JUMP)

        if (horizontalDist < ANTI_SPIN_DIST) {
            InputManager.press(MoveAction.FORWARD)
            return
        }

        val jumping = needsWaypointJump || needsTerrainJump
        val upcomingJumpTarget = findUpcomingJumpTarget(waypoints, idx, pos)
        val effectiveTarget = upcomingJumpTarget ?: target
        val effectiveJumping = jumping || upcomingJumpTarget != null
        val edx = effectiveTarget.x - pos.x
        val edz = effectiveTarget.z - pos.z
        val (lookDx, lookDz) = if (effectiveJumping) edx to edz else computeTangent(waypoints, idx, pos, dx, dz, horizontalDist)
        val (blendedDx, blendedDz) = if (effectiveJumping) edx to edz else lookDx to lookDz
        val pitchOffset = if (effectiveJumping) 0.0 else computePitchFromSlope(waypoints, idx, blendedDx, blendedDz)
        val lookTargetY = if (effectiveJumping) effectiveTarget.y else (player.eyeY + pitchOffset)
        val lookTargetX = pos.x + blendedDx
        val lookTargetZ = pos.z + blendedDz
        val lookAngles = AngleUtils.calcAimAnglesFromDelta(lookTargetX - pos.x, lookTargetY - player.eyeY, lookTargetZ - pos.z)
        desiredYaw = lookAngles.first
        desiredPitch = lookAngles.second

        val movementYaw = AngleUtils.calcAimAnglesFromDelta(dx, 0.0, dz).first
        val yawDiff = AngleUtils.wrapDegrees(movementYaw - player.yRot)
        val absYawDiff = abs(yawDiff)

        if (absYawDiff > BACKWARD_YAW) {
            InputManager.press(MoveAction.BACKWARD)
            if (absYawDiff < BACKWARD_NO_STRAFE_YAW) {
                if (yawDiff > 0f) InputManager.press(MoveAction.RIGHT)
                else InputManager.press(MoveAction.LEFT)
            }
            return
        } else if (absYawDiff > STRAFE_YAW_MIN) {
            if (yawDiff > 0f) InputManager.press(MoveAction.RIGHT)
            else InputManager.press(MoveAction.LEFT)
        }

        val effectiveYawDiff = blendUpcomingTurnIntoYaw(waypoints, idx, target, dx, dz, horizontalDist, absYawDiff)
        if (enableSpeedAdaptation) {
            when {
                effectiveYawDiff < SPEED_SPRINT_YAW -> {
                    InputManager.press(MoveAction.FORWARD)
                    if (enableSprint) InputManager.press(MoveAction.SPRINT)
                }
                effectiveYawDiff < SPEED_JOG_YAW -> InputManager.press(MoveAction.FORWARD)
                effectiveYawDiff < SPEED_WALK_YAW -> InputManager.press(MoveAction.FORWARD)
                // else: don't press FORWARD, let rotation catch up
            }
        } else {
            InputManager.press(MoveAction.FORWARD)
            if (enableSprint && absYawDiff < 60f) InputManager.press(MoveAction.SPRINT)
        }
    }

    private fun findUpcomingJumpTarget(waypoints: List<Vec3>, idx: Int, pos: Vec3): Vec3? {
        val ceiling = min(idx + JUMP_LOOKAHEAD_WAYPOINTS, waypoints.lastIndex)
        for (i in idx..ceiling) {
            val wp = waypoints[i]
            val prev = if (i > 0) waypoints[i - 1] else pos
            val dy = wp.y - prev.y
            if (dy < 0.9 || dy > STEP_UP_MAX_HEIGHT) continue
            val dx = wp.x - pos.x
            val dz = wp.z - pos.z
            val dist = sqrt(dx * dx + dz * dz)
            if (dist <= JUMP_LOOKAHEAD_DIST) return wp
        }
        return null
    }

    private fun steerFall(target: Vec3, pos: Vec3, player: LocalPlayer, dx: Double, dz: Double) {
        val lookAngles = AngleUtils.calcAimAnglesFromDelta(dx, target.y - player.eyeY, dz)
        desiredYaw = lookAngles.first
        desiredPitch = lookAngles.second
        val xzDrift = abs(pos.x + player.deltaMovement.x - target.x) + abs(pos.z + player.deltaMovement.z - target.z)
        if (xzDrift > 0.2 && abs(player.deltaMovement.y) > 0.4) {
            InputManager.press(MoveAction.SNEAK)
        }
        InputManager.press(MoveAction.FORWARD)
    }

    private fun computeTangent(waypoints: List<Vec3>, idx: Int, pos: Vec3, dx: Double, dz: Double, horizontalDist: Double): Pair<Double, Double> {
        var tDx = 0.0
        var tDz = 0.0
        var tWeight = 0.0

        if (horizontalDist > 0.01) {
            val w = horizontalDist * TANGENT_CURRENT_WEIGHT_MULT
            tDx += (dx / horizontalDist) * w
            tDz += (dz / horizontalDist) * w
            tWeight += w
        }

        var accDist = 0.0
        val maxSegs = min(TANGENT_MAX_SEGMENTS, waypoints.size - idx - 1)
        for (i in 0 until maxSegs) {
            val segStart = waypoints[idx + i]
            val segEnd = waypoints[idx + i + 1]
            val sDx = segEnd.x - segStart.x
            val sDz = segEnd.z - segStart.z
            val sLen = sqrt(sDx * sDx + sDz * sDz)
            if (sLen < 0.01) continue
            accDist += sLen
            if (accDist > TANGENT_MAX_DISTANCE) break
            val distFactor = 1.0 - (accDist / TANGENT_MAX_DISTANCE)
            val w = sLen * distFactor
            tDx += (sDx / sLen) * w
            tDz += (sDz / sLen) * w
            tWeight += w
        }

        if (tWeight <= 0.01) return dx to dz
        var resultDx = tDx / tWeight
        var resultDz = tDz / tWeight
        val tLen = sqrt(resultDx * resultDx + resultDz * resultDz)
        if (tLen > 0.01) {
            val lookDist = max(LOOK_DIST_MIN, horizontalDist)
            resultDx = (resultDx / tLen) * lookDist
            resultDz = (resultDz / tLen) * lookDist
        }
        return resultDx to resultDz
    }

    private fun applyPreTurnBlend(
        waypoints: List<Vec3>, idx: Int, target: Vec3,
        dx: Double, dz: Double, horizontalDist: Double,
        lookDx: Double, lookDz: Double
    ): Pair<Double, Double> {
        if (idx + 1 >= waypoints.size) return lookDx to lookDz
        val nextWp = waypoints[idx + 1]
        val nextDx = nextWp.x - target.x
        val nextDz = nextWp.z - target.z
        val nextLen = sqrt(nextDx * nextDx + nextDz * nextDz)
        if (nextLen <= 0.5) return lookDx to lookDz

        val curYaw = (Math.toDegrees(atan2(-dx, dz))).toFloat()
        val nextYaw = (Math.toDegrees(atan2(-nextDx, nextDz))).toFloat()
        val turnAngle = abs(AngleUtils.wrapDegrees(nextYaw - curYaw))
        if (turnAngle <= PRE_TURN_THRESHOLD) return lookDx to lookDz

        val rangeSpan = PRE_TURN_BLEND_RANGE - ANTI_SPIN_DIST
        if (rangeSpan <= 0) return lookDx to lookDz
        val rawBlend = (1.0 - ((horizontalDist - ANTI_SPIN_DIST) / rangeSpan)).coerceIn(0.0, PRE_TURN_BLEND_MAX)
        if (rawBlend <= 0.005) return lookDx to lookDz

        val tLen = sqrt(lookDx * lookDx + lookDz * lookDz)
        if (tLen <= 0.01) return lookDx to lookDz
        val curDirX = lookDx / tLen
        val curDirZ = lookDz / tLen
        val nDirX = nextDx / nextLen
        val nDirZ = nextDz / nextLen
        return (curDirX + rawBlend * (nDirX - curDirX)) * tLen to (curDirZ + rawBlend * (nDirZ - curDirZ)) * tLen
    }

    private fun computePitchFromSlope(waypoints: List<Vec3>, idx: Int, lookDx: Double, lookDz: Double): Double {
        if (idx + 1 >= waypoints.size) return 0.0
        var totalSlope = 0.0
        var totalWeight = 0.0
        var accDist = 0.0
        val maxSegs = min(PITCH_MAX_SEGMENTS, waypoints.size - idx - 1)
        for (i in 0 until maxSegs) {
            val segStart = waypoints[idx + i]
            val segEnd = waypoints[idx + i + 1]
            val sDx = segEnd.x - segStart.x
            val sDz = segEnd.z - segStart.z
            val sHDist = sqrt(sDx * sDx + sDz * sDz)
            val sDy = segEnd.y - segStart.y
            if (sHDist < 0.01) continue
            accDist += sHDist
            if (accDist > PITCH_MAX_DISTANCE) break
            val slope = sDy / sHDist
            val distFactor = 1.0 - (accDist / PITCH_MAX_DISTANCE)
            val w = sHDist * distFactor
            totalSlope += slope * w
            totalWeight += w
        }
        if (totalWeight <= 0.01) return 0.0
        val avgSlope = totalSlope / totalWeight
        val hLookDist = sqrt(lookDx * lookDx + lookDz * lookDz)
        return (avgSlope * min(hLookDist, PITCH_HORIZONTAL_CAP) * PITCH_SLOPE_SCALE).coerceIn(-PITCH_CLAMP, PITCH_CLAMP)
    }

    private fun blendUpcomingTurnIntoYaw(
        waypoints: List<Vec3>, idx: Int, target: Vec3,
        dx: Double, dz: Double, horizontalDist: Double, absYawDiff: Float
    ): Float {
        if (idx + 1 >= waypoints.size) return absYawDiff
        if (horizontalDist >= PRE_TURN_BLEND_RANGE) return absYawDiff
        val nextWp = waypoints[idx + 1]
        val nextSegDx = nextWp.x - target.x
        val nextSegDz = nextWp.z - target.z
        val nextSegLen = sqrt(nextSegDx * nextSegDx + nextSegDz * nextSegDz)
        if (nextSegLen <= 0.3) return absYawDiff
        val curSegYaw = (Math.toDegrees(atan2(-dx, dz))).toFloat()
        val nextSegYaw = (Math.toDegrees(atan2(-nextSegDx, nextSegDz))).toFloat()
        val upcomingTurnAngle = abs(AngleUtils.wrapDegrees(nextSegYaw - curSegYaw))
        if (upcomingTurnAngle <= absYawDiff) return absYawDiff
        val blendFactor = (1.0 - (horizontalDist / PRE_TURN_BLEND_RANGE)).coerceIn(0.0, 0.6).toFloat()
        return absYawDiff + blendFactor * (upcomingTurnAngle - absYawDiff)
    }

    private fun terrainNeedsPrejump(player: LocalPlayer, dx: Double, dz: Double, horizontalDist: Double): Boolean {
        if (!player.onGround() || horizontalDist < 0.3) return false
        val nx = dx / horizontalDist
        val nz = dz / horizontalDist
        val feetY = player.y
        val cursor = BlockPos.MutableBlockPos()
        for (probeDist in PROBE_DISTANCES) {
            val px = player.x + nx * probeDist
            val pz = player.z + nz * probeDist
            val low = floor(feetY + PROBE_DEPTH_BELOW).toInt()
            val high = floor(feetY + BlockCache.PLAYER_HEIGHT).toInt()
            for (by in low..high) {
                cursor.set(floor(px).toInt(), by, floor(pz).toInt())
                val shape = BlockCache.getCollisionShape(cursor)
                if (shape.isEmpty) continue
                val top = by + shape.max(Direction.Axis.Y)
                val bottom = by + shape.min(Direction.Axis.Y)
                val stepHeight = top - feetY
                if (stepHeight > STEP_UP_MIN_HEIGHT && stepHeight <= STEP_UP_MAX_HEIGHT && top > feetY + GROUND_TOLERANCE) return true
                if (bottom > feetY + GROUND_TOLERANCE && bottom < feetY + BlockCache.PLAYER_HEIGHT && top > feetY + STEP_UP_MIN_HEIGHT) return true
            }
        }
        return false
    }

    private fun ledgeNeedsBridge(player: LocalPlayer, dx: Double, dz: Double, horizontalDist: Double): Boolean {
        if (!player.onGround() || horizontalDist < 0.8) return false
        val nx = dx / horizontalDist
        val nz = dz / horizontalDist
        val feetY = player.y
        val farTop = surfaceTopNear(player.x + nx * LEDGE_PROBE_FAR, player.z + nz * LEDGE_PROBE_FAR, feetY, true) ?: return false
        if (farTop <= feetY + STEP_UP_MIN_HEIGHT || farTop > feetY + STEP_UP_MAX_HEIGHT) return false
        val midTop = surfaceTopNear(player.x + nx * LEDGE_PROBE_NEAR, player.z + nz * LEDGE_PROBE_NEAR, feetY, false)
        return midTop == null || midTop < feetY - GROUND_TOLERANCE
    }

    private fun surfaceTopNear(x: Double, z: Double, feetY: Double, allowJumpHigh: Boolean): Double? {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        val maxOffset = if (allowJumpHigh) 1 else 0
        val cursor = BlockPos.MutableBlockPos()
        for (yOff in maxOffset downTo -2) {
            val by = floor(feetY).toInt() + yOff
            cursor.set(bx, by, bz)
            val shape = BlockCache.getCollisionShape(cursor)
            if (shape.isEmpty) continue
            val top = by + shape.max(Direction.Axis.Y)
            val limit = if (allowJumpHigh) STEP_UP_MAX_HEIGHT else STEP_UP_MIN_HEIGHT
            if (top in (feetY - 1.5)..(feetY + limit)) return top
        }
        return null
    }

    private fun applyYawEasing() {
        val player = mc.player ?: return
        if (!desiredYaw.isNaN()) {
            val delta = AngleUtils.wrapDegrees(desiredYaw - player.yRot)
            val slew = if (abs(delta) > YAW_SLEW_FAR_THRESHOLD) YAW_SLEW_FAR else YAW_SLEW_NEAR
            player.yRot += delta * slew
        }
        if (!desiredPitch.isNaN()) {
            val deltaPitch = desiredPitch - player.xRot
            player.xRot += deltaPitch * PITCH_SLEW
        }
    }

    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        val active = plan
        if (active is RoutePlan.Failed) return
        applyYawEasing()
    }
}
