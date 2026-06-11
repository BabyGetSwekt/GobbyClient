package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.pathfinder.movement.InputManager
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PlayerUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min

object PathExecutor {

    var enableMicroPauses: Boolean = true
    var enableSpeedAdaptation: Boolean = true
    var enableSprint: Boolean = true
    var enableSegmented: Boolean = false
    var segmentBlocks: Int = DEFAULT_SEGMENT_SIZE

    private var plan: RoutePlan = RoutePlan.Failed
    private var cursor: Int = 0
    private val steering = PathSteering()
    private val recovery = PathRecoveryMonitor()
    private val microPauser = PathMicroPauser()
    private var lookAheadCooldown: Int = 0
    private var dynamicRepathCooldown: Int = 0
    private var obstructionHits: Int = 0
    private var graceTicksRemaining: Int = 0
    private var repathInFlight: Boolean = false
    private var pendingFallRepath: Boolean = false
    private var partialReplans: Int = 0
    private var finalGoal: Vec3? = null
    private var travelMode: TravelMode = TravelMode.WALK
    private val segmentContinuation = PathSegmentContinuation()
    private var waypointsMutable: MutableList<Vec3> = mutableListOf()

    fun beginLongPath(start: Vec3, finalGoal: Vec3, mode: TravelMode = TravelMode.WALK) {
        enableSegmented = false
        partialReplans = 0
        planFullAsync(start, finalGoal, mode)
    }

    private fun planFullAsync(start: Vec3, finalGoal: Vec3, mode: TravelMode) {
        RouteEngine.planAsync(start, finalGoal, mode).thenAccept { plan ->
            mc.execute {
                if (plan is RoutePlan.Failed) {
                    modMessage("§cNo route found in ${PlanStats.lastTotalMs}ms (${PlanStats.lastPolygonCount} polys scanned).")
                } else {
                    PathRouteReporter.reportTimings(plan, segmented = false)
                    begin(plan, finalGoal, mode)
                }
            }
        }
    }


    fun begin(routePlan: RoutePlan, originalGoal: Vec3? = null, mode: TravelMode = TravelMode.WALK) {
        if (routePlan.isEmpty) {
            modMessage("§cNo route to follow.")
            return
        }
        plan = routePlan
        if (routePlan is RoutePlan.Ground && routePlan.complete) partialReplans = 0
        waypointsMutable = routePlan.waypoints.toMutableList()
        cursor = 1
        steering.reset()
        recovery.reset()
        microPauser.reset()
        lookAheadCooldown = LOOK_AHEAD_INTERVAL
        dynamicRepathCooldown = DYNAMIC_REPATH_INTERVAL
        obstructionHits = 0
        graceTicksRemaining = OBSTRUCTION_GRACE_TICKS
        repathInFlight = false
        pendingFallRepath = false
        segmentContinuation.reset(enableSegmented, originalGoal, routePlan.waypoints.last())
        finalGoal = originalGoal ?: routePlan.waypoints.last()
        travelMode = mode
    }

    fun stop() {
        if (plan === RoutePlan.Failed) return
        plan = RoutePlan.Failed
        cursor = 0
        steering.reset()
        recovery.reset()
        repathInFlight = false
        pendingFallRepath = false
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
        cursor = PathProgress.advanceCursor(cursor, waypoints, pos, isSky, player.onGround())
        if (cursor >= waypoints.size) {
            arrive()
            return
        }

        if (segmentContinuation.shouldRequestNextSegment(cursor, waypoints.size)) {
            segmentContinuation.requestNextSegment(
                waypointsMutable,
                finalGoal,
                segmentBlocks,
                travelMode,
                stop = ::stop,
                replanFromCurrentPosition = ::triggerObstructionRepath
            )
        }

        if (!isSky && handleUnplannedFall(waypoints, pos, player)) return

        when (val decision = recovery.tick(pos, isSky, cursor, waypoints, repathInFlight)) {
            PathRecoveryMonitor.Decision.None -> Unit
            is PathRecoveryMonitor.Decision.SkipTo -> cursor = decision.cursor
            PathRecoveryMonitor.Decision.OffPath -> {
                triggerOffPathRepath(pos)
                return
            }
            PathRecoveryMonitor.Decision.Stuck -> {
                triggerStuckRepath(pos)
                return
            }
        }
        if (repathInFlight) return
        if (recovery.applyRecoveryInputsIfNeeded()) return

        if (microPauser.tick(enableMicroPauses)) return

        if (!isSky && dynamicRepathDueAndBlocked(waypoints)) {
            triggerObstructionRepath(pos)
            return
        }

        if (!isSky) tryLookAheadShortcut(waypoints, pos, player)
        if (cursor >= waypoints.size) { arrive(); return }

        if (isSky) steering.steerSky(waypoints[cursor], pos, player)
        else steering.steerGround(waypoints, cursor, pos, player, enableSpeedAdaptation, enableSprint)
    }

    private fun dynamicRepathDueAndBlocked(waypoints: List<Vec3>): Boolean {
        if (mc.player?.onGround() == false) return false
        if (graceTicksRemaining > 0) { graceTicksRemaining--; return false }
        dynamicRepathCooldown--
        if (dynamicRepathCooldown > 0) return false
        dynamicRepathCooldown = DYNAMIC_REPATH_INTERVAL
        val ceiling = min(cursor + DYNAMIC_REPATH_LOOKAHEAD, waypoints.lastIndex)
        var blocked = false
        for (i in cursor until ceiling) {
            if (!PathCollision.isSegmentStillClear(waypoints[i], waypoints[i + 1])) { blocked = true; break }
        }
        if (!blocked) { obstructionHits = 0; return false }
        obstructionHits++
        return obstructionHits >= OBSTRUCTION_CONFIRM_HITS
    }

    private fun handleUnplannedFall(waypoints: List<Vec3>, pos: Vec3, player: LocalPlayer): Boolean {
        if (cursor <= 0 || cursor >= waypoints.size) return false

        if (pendingFallRepath) {
            InputManager.releaseAll()
            if (player.onGround() && !repathInFlight) {
                pendingFallRepath = false
                triggerOffPathRepath(pos)
            }
            return true
        }

        if (player.onGround()) return false
        val deviation = PathFollowMath.segmentDeviation(waypoints, cursor, pos) ?: return false
        if (deviation.segmentDy < -0.2) return false

        val fellBelowPath = deviation.verticalBelow > FALL_OFF_PATH_Y_DROP
        val fellSideways = deviation.lateral > FALL_OFF_PATH_LATERAL
        if (!fellBelowPath && !fellSideways) return false

        pendingFallRepath = true
        recovery.clearForFall()
        InputManager.releaseAll()
        return true
    }

    private fun triggerRepath(pos: Vec3, reason: String) {
        val goal = finalGoal ?: return
        if (repathInFlight) return
        repathInFlight = true
        recovery.reset()
        InputManager.releaseAll()
        modMessage(reason)
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

    private fun triggerOffPathRepath(pos: Vec3) = triggerRepath(pos, "§eOff-path detected - recalculating...")

    private fun triggerObstructionRepath(pos: Vec3) = triggerRepath(pos, "§eRoute obstructed, recalculating...")

    private fun triggerStuckRepath(pos: Vec3) {
        PathBlacklist.blacklistArea(pos, STUCK_BLACKLIST_RADIUS, STUCK_BLACKLIST_DURATION)
        plan.waypoints.getOrNull(cursor)?.let {
            PathBlacklist.blacklistArea(it, STUCK_BLACKLIST_WAYPOINT_RADIUS, STUCK_BLACKLIST_DURATION)
        }
        triggerRepath(pos, "§cStuck! Blacklisting and replanning...")
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
            if (PathCollision.quickLineOfSight(pos, wp)) {
                cursor = i
                break
            }
        }
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
                    partialReplans++
                    if (partialReplans > MAX_PARTIAL_REPLANS) {
                        modMessage("§cGoal appears unreachable after $MAX_PARTIAL_REPLANS partial paths - giving up.")
                        stop()
                        return
                    }
                    triggerRepath(pos, "§eReached end of partial path - replanning... ($partialReplans/$MAX_PARTIAL_REPLANS)")
                }
                return
            }
        }
        modMessage("§aRoute complete!")
        stop()
    }


    @SubscribeEvent
    fun onRender3D(event: NewRender3DEvent) {
        val active = plan
        if (active is RoutePlan.Failed) return
        steering.applyYawEasing()
    }
}
