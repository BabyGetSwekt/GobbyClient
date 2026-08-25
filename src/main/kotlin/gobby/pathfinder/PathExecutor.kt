package gobby.pathfinder

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.pathfinder.movement.InputManager
import gobby.pathfinder.prediction.JumpTracker
import gobby.pathfinder.prediction.PredictionLogger
import gobby.utils.ChatUtils.modMessage
import gobby.utils.PlayerUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

object PathExecutor {
    var enableMicroPauses: Boolean = true
    var enableSpeedAdaptation: Boolean = true
    var enableSprint: Boolean = true
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
    private val fallRecovery = PathFallRecovery(recovery)
    private var partialReplans: Int = 0
    private var bestGoalDist: Double = Double.MAX_VALUE
    private var finalGoal: Vec3? = null
    private var travelMode: TravelMode = TravelMode.WALK
    private var waypointsMutable: MutableList<Vec3> = mutableListOf()

    fun beginLongPath(start: Vec3, finalGoal: Vec3, mode: TravelMode = TravelMode.WALK) {
        partialReplans = 0
        bestGoalDist = Double.MAX_VALUE
        PredictionLogger.startRoute(finalGoal)
        PredictionLogger.log("autoJump=${mc.options.autoJump().get()}")
        JumpTracker.reset()
        planFullAsync(start, finalGoal, mode)
    }

    private fun planFullAsync(start: Vec3, finalGoal: Vec3, mode: TravelMode) {
        RouteEngine.planAsync(start, finalGoal, mode).thenAccept { plan ->
            mc.execute {
                if (plan is RoutePlan.Failed) {
                    modMessage("§cNo route found in ${PlanStats.lastTotalMs}ms.")
                } else {
                    PathRouteReporter.reportTimings(plan)
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
        val goal = originalGoal ?: routePlan.waypoints.last()
        if (routePlan is RoutePlan.Ground && routePlan.complete && endsNearGoal(routePlan, goal)) partialReplans = 0
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
        fallRecovery.reset()
        finalGoal = goal
        travelMode = mode
    }

    private fun endsNearGoal(routePlan: RoutePlan, goal: Vec3): Boolean {
        val end = routePlan.waypoints.last()
        val dx = end.x - goal.x
        val dz = end.z - goal.z
        return dx * dx + dz * dz <= GOAL_ARRIVE_DIST_SQ && abs(end.y - goal.y) <= GOAL_ARRIVE_Y_DIFF
    }

    fun stop() {
        if (plan === RoutePlan.Failed) return
        plan = RoutePlan.Failed
        cursor = 0
        steering.reset()
        recovery.reset()
        repathInFlight = false
        fallRecovery.reset()
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
        JumpTracker.tick(player)
        processTick(active, player)
    }

    private fun processTick(active: RoutePlan, player: LocalPlayer) {
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
        if (!isSky && fallRecovery.handle(waypoints, cursor, pos, player) { triggerOffPathRepath(pos) }) return

        if (handleRecovery(pos, isSky, waypoints)) return
        if (repathInFlight) return
        if (recovery.applyRecoveryInputsIfNeeded()) return
        if (microPauser.tick(enableMicroPauses)) return
        if (!isSky && dynamicRepathDueAndBlocked(waypoints)) {
            triggerObstructionRepath(pos)
            return
        }

        if (!isSky) {
            val shortcut = PathLookaheadShortcut.find(waypoints, cursor, pos, player, lookAheadCooldown)
            cursor = shortcut.first
            lookAheadCooldown = shortcut.second
        }
        if (cursor >= waypoints.size) {
            arrive()
            return
        }

        if (isSky) steering.steerSky(waypoints[cursor], pos, player)
        else steering.steerGround(waypoints, cursor, pos, player, enableSpeedAdaptation, enableSprint)
    }

    private fun handleRecovery(pos: Vec3, isSky: Boolean, waypoints: List<Vec3>): Boolean {
        when (val decision = recovery.tick(pos, isSky, cursor, waypoints, repathInFlight)) {
            PathRecoveryMonitor.Decision.None -> return false
            is PathRecoveryMonitor.Decision.SkipTo -> cursor = decision.cursor
            PathRecoveryMonitor.Decision.OffPath -> {
                triggerOffPathRepath(pos)
                return true
            }
            PathRecoveryMonitor.Decision.Stuck -> {
                triggerStuckRepath(pos)
                return true
            }
        }
        return false
    }

    private fun dynamicRepathDueAndBlocked(waypoints: List<Vec3>): Boolean {
        if (mc.player?.onGround() == false) return false
        if (graceTicksRemaining > 0) {
            graceTicksRemaining--
            return false
        }
        dynamicRepathCooldown--
        if (dynamicRepathCooldown > 0) return false
        dynamicRepathCooldown = DYNAMIC_REPATH_INTERVAL
        val ceiling = min(cursor + DYNAMIC_REPATH_LOOKAHEAD, waypoints.lastIndex)
        var blocked = false
        for (i in cursor until ceiling) {
            if (!PathCollision.isSegmentStillClear(waypoints[i], waypoints[i + 1])) {
                blocked = true
                break
            }
        }
        if (!blocked) {
            obstructionHits = 0
            return false
        }
        obstructionHits++
        return obstructionHits >= OBSTRUCTION_CONFIRM_HITS
    }

    private fun triggerRepath(pos: Vec3, reason: String) {
        val goal = finalGoal ?: return
        if (repathInFlight) return
        repathInFlight = true
        recovery.reset()
        InputManager.releaseAll()
        modMessage(reason)
        PathRepathPlanner.submit(pos, goal, travelMode, { newPlan ->
            begin(newPlan, goal, travelMode)
        }, {
            modMessage("§cReroute failed.")
            stop()
        })
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

    private fun arrive() {
        val player = mc.player
        if (player != null && !player.onGround() && plan !is RoutePlan.Sky) return
        val pos = player?.position()
        val goal = finalGoal
        if (pos != null && goal != null) {
            val dx = pos.x - goal.x
            val dz = pos.z - goal.z
            val planar = dx * dx + dz * dz
            val dy = abs(pos.y - goal.y)
            if (planar > GOAL_ARRIVE_DIST_SQ || dy > GOAL_ARRIVE_Y_DIFF) {
                if (!repathInFlight) {
                    val goalDist = sqrt(planar + dy * dy)
                    if (bestGoalDist - goalDist > PARTIAL_PROGRESS_RESET_DIST) partialReplans = 0
                    bestGoalDist = min(bestGoalDist, goalDist)
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
