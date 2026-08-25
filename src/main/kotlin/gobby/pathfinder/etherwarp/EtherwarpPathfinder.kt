package gobby.pathfinder.etherwarp

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import gobby.pathfinder.search.HybridSearchPolicy
import gobby.pathfinder.search.EtherwarpSearchOutcome
import gobby.pathfinder.search.SearchCandidateOrder
import gobby.pathfinder.search.IntGenerationSet
import gobby.pathfinder.search.SearchLane
import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.navigation.PreparedDungeonRouteCache
import gobby.pathfinder.navigation.CachedRouteValidation
import gobby.pathfinder.navigation.CompiledRoomLandingAtlas
import gobby.pathfinder.navigation.CustomRoomLandings
import gobby.pathfinder.navigation.DependencyValidatedRoute
import gobby.pathfinder.navigation.EdgeDependency
import gobby.pathfinder.navigation.PlannerMemo
import gobby.pathfinder.navigation.PlannerMemoScope
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

object EtherwarpPathfinder {
    private const val MIN_CONE_DEG = 50.0
    private const val BACKTRACK_CONE_DEG = 45.0
    private val legacyWorkers = Executors.newFixedThreadPool(EtherwarpPathConfig.DEFAULT_THREADS) { task ->
        Thread(task, "gobby-etherwarp-fallback").apply { isDaemon = true }
    }
    private val legacySeen = ThreadLocal.withInitial { LongOpenHashSet(INITIAL_SEEN_CAPACITY) }
    private val legacyCandidates = ThreadLocal.withInitial { IntGenerationSet() }

    fun preload() {
        val config = EtherwarpPathConfig()
        val kind = EtherwarpKind.ETHERWARP
        CompiledRoomLandingAtlas.preload()
        CustomRoomLandings.preload()
        PrimitiveEtherwarpSearch.preload()
        kind.eyeHeight()
        RaycastCache.get(kind.searchRange(config), config.pitchStep, config.yawStep)
        EtherwarpUtils.directionFromAngles(ZERO_ANGLE, ZERO_ANGLE)
    }

    fun preload(snapshot: BlockCache.SnapshotView) = EtherwarpRuntimeWarmup.preload(snapshot)

    fun preload(snapshot: BlockCache.SnapshotView, grid: Array<gobby.utils.skyblock.dungeon.map.MapTile>, mapRevision: Long = 0L) = EtherwarpRuntimeWarmup.preload(snapshot, grid, mapRevision)

    fun preloadAsync(
        snapshot: BlockCache.SnapshotView,
        grid: Array<gobby.utils.skyblock.dungeon.map.MapTile>,
        mapRevision: Long = 0L,
        onFinished: (Boolean) -> Unit
    ) = EtherwarpRuntimeWarmup.preloadAsync(snapshot, grid, mapRevision, onFinished)

    fun cancelPreload() = EtherwarpRuntimeWarmup.cancel()

    fun findPath(
        from: Vec3,
        to: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig = EtherwarpPathConfig()
    ): List<EtherwarpNode>? {
        val cache = BlockCache.freeze().trackingMissingChunks()
        PreparedDungeonRouteCache.take(from, to, DIRECT_CACHE_CELL, false, kind, config, cache, DIRECT_CACHE_REVISION)?.let { return it }
        if (recordUnavailableExactGoal(from, to, kind, config, cache, DIRECT_CACHE_CELL, DIRECT_CACHE_REVISION)) return null
        val outcome = searchOutcome(from, to, kind, config, { it == to }, cache.trackingMissingChunks(), SearchDeadline(config.timeout))
        val route = outcome.path?.let { smooth(it, kind.searchRange(config), kind, cache) }
        if (route != null) {
            PreparedDungeonRouteCache.put(route, to, DIRECT_CACHE_CELL, false, kind, config, cache, DIRECT_CACHE_REVISION)
        } else if (outcome === EtherwarpSearchOutcome.NoRoute) {
            PreparedDungeonRouteCache.recordFailure(from, to, DIRECT_CACHE_CELL, false, kind, config, cache, DIRECT_CACHE_REVISION)
        }
        return route
    }

    fun search(from: Vec3, goal: BlockPos, kind: EtherwarpKind, config: EtherwarpPathConfig = EtherwarpPathConfig(), reached: (BlockPos) -> Boolean = { it == goal }, cache: BlockCache.SnapshotView? = null, deadline: SearchDeadline = SearchDeadline(config.timeout), lane: SearchLane = SearchLane.HYBRID, landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL): List<EtherwarpNode>? {
        if (deadline.expired) return null
        val cacheView = (cache ?: BlockCache.freeze()).trackingMissingChunks()
        return searchOutcome(from, goal, kind, config, reached, cacheView, deadline, lane, landingFilter).path
    }

    fun searchOutcome(from: Vec3, goal: BlockPos, kind: EtherwarpKind, config: EtherwarpPathConfig, reached: (BlockPos) -> Boolean, cacheView: BlockCache.SnapshotView, deadline: SearchDeadline, lane: SearchLane = SearchLane.HYBRID, landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL): EtherwarpSearchOutcome = PlannerMemoScope.current()?.let {
        searchOutcomeInternal(from, goal, kind, config, reached, cacheView, deadline, lane, landingFilter)
    } ?: PlannerMemoScope.with(PlannerMemo()) {
        searchOutcomeInternal(from, goal, kind, config, reached, cacheView, deadline, lane, landingFilter)
    }

    private fun searchOutcomeInternal(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        cacheView: BlockCache.SnapshotView,
        deadline: SearchDeadline,
        lane: SearchLane,
        landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL
    ): EtherwarpSearchOutcome {
        if (deadline.expired) return EtherwarpSearchOutcome.TimedOut
        EtherwarpDirectRoute.find(from, goal, kind, config, reached, cacheView)?.let { return EtherwarpSearchOutcome.Found(it) }
        if (kind == EtherwarpKind.ETHERWARP) {
            val range = kind.searchRange(config)
            EtherwarpHopField.forGoal(goal, range)?.query(from, range)?.takeIf { path ->
                path.lastOrNull()?.pos?.let(reached) == true
            }?.let { return EtherwarpSearchOutcome.Found(it) }
        }
        val primitiveView = cacheView.isolatedTrackingReads()
        val primitiveDeadline = if (lane == SearchLane.PRIMITIVE_ONLY) deadline
        else deadline.child(HybridSearchPolicy.forTimeout(config.timeout).fastMillis)
        val primitive = PrimitiveEtherwarpSearch.find(from, goal, kind, config, reached, primitiveView, primitiveDeadline, landingFilter)
        primitive.path?.let { return EtherwarpSearchOutcome.Found(it) }
        if (primitive.termination == PrimitiveSearchTermination.INTERRUPTED) return EtherwarpSearchOutcome.Interrupted
        if (lane == SearchLane.PRIMITIVE_ONLY) return primitive.toSearchOutcome(primitiveView)
        val artifactView = cacheView.isolatedTrackingReads()
        val access = EtherwarpUtils.cachedAccess(artifactView)
            ?: return EtherwarpSearchOutcome.MissingChunks(missingChunkKeys(cacheView, primitiveView, artifactView))
        val path = searchWith(from, goal, kind, config, reached, access, deadline, landingFilter)
        if (path != null) return EtherwarpSearchOutcome.Found(path)
        if (Thread.currentThread().isInterrupted) return EtherwarpSearchOutcome.Interrupted
        if (cacheView.missingChunksAccessed().isNotEmpty() ||
            primitiveView.missingChunksAccessed().isNotEmpty() ||
            artifactView.missingChunksAccessed().isNotEmpty()
        ) return EtherwarpSearchOutcome.MissingChunks(missingChunkKeys(cacheView, primitiveView, artifactView))
        return if (deadline.expired) EtherwarpSearchOutcome.TimedOut else EtherwarpSearchOutcome.NoRoute
    }

    private fun missingChunkKeys(vararg snapshots: BlockCache.SnapshotView): Set<Long> = snapshots
        .asSequence()
        .flatMap { it.missingChunksAccessed().asSequence() }
        .toSet()

    fun searchWith(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        access: EtherwarpWorldAccess,
        deadline: SearchDeadline = SearchDeadline(config.timeout),
        landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL
    ): List<EtherwarpNode>? {
        val range = kind.searchRange(config)
        val raycasts = RaycastCache.get(range, config.pitchStep, config.yawStep)
        val start = BlockPos.containing(from)
        val ctx = EtherwarpContext(goal, range, config.hWeight, raycasts, deadline, reached, LandingPolicy.upTo(maxLandingY(kind, start.y, goal.y), landingFilter))
        ctx.offer(EtherwarpNode(from.x, from.y, from.z, start, 0.0, heuristic(VecUtils.distance(start, goal), range, config.hWeight), null, 0f, 0f))
        val workers = List(config.threads.coerceIn(1, EtherwarpPathConfig.DEFAULT_THREADS) - 1) {
            legacyWorkers.submit { worker(ctx, kind, access) }
        }
        worker(ctx, kind, access)
        awaitWorkers(ctx, workers)
        pathLog { "      legacy search from=${PathPlanDiagnostics.describeVec(from)} goal=${PathPlanDiagnostics.describeBlock(goal)} processed=${ctx.processed.get()} elapsed=${ctx.elapsed}ms timedOut=${ctx.timedOut} hops=${ctx.result?.let { it.size - 1 } ?: -1} range=$range" }
        return ctx.result
    }

    fun exactDirect(from: Vec3, goal: BlockPos, kind: EtherwarpKind, config: EtherwarpPathConfig, reached: (BlockPos) -> Boolean, snapshot: BlockCache.SnapshotView): List<EtherwarpNode>? = EtherwarpDirectRoute.find(from, goal, kind, config, reached, snapshot)

    fun revalidateSnapshot(path: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): List<EtherwarpNode>? = EtherwarpRouteValidator.validate(path, range, kind, snapshot)?.nodes

    fun revalidateSnapshotWithDependencies(path: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): DependencyValidatedRoute? = EtherwarpRouteValidator.validate(path, range, kind, snapshot)

    fun revalidateCachedRoute(path: List<EtherwarpNode>, dependencies: List<EdgeDependency>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView): CachedRouteValidation = EtherwarpRouteValidator.validateCached(path, dependencies, range, kind, snapshot)

    private fun awaitWorkers(ctx: EtherwarpContext, workers: List<java.util.concurrent.Future<*>>) {
        try {
            workers.forEach { worker -> worker.get(ctx.remainingNanos, TimeUnit.NANOSECONDS) }
        } catch (_: InterruptedException) {
            EtherwarpWorkerCancellation.cancel(ctx, workers, timedOut = false)
            Thread.currentThread().interrupt()
        } catch (_: TimeoutException) {
            EtherwarpWorkerCancellation.cancel(ctx, workers, timedOut = true)
        }
    }

    private fun worker(ctx: EtherwarpContext, kind: EtherwarpKind, access: EtherwarpWorldAccess) {
        while (!ctx.solved) {
            if (Thread.currentThread().isInterrupted) {
                ctx.solved = true
                return
            }
            if (ctx.expired) {
                ctx.timedOut = true
                ctx.solved = true
                return
            }
            val current = ctx.next()
            if (current == null) {
                if (ctx.done) ctx.solved = true else Thread.yield()
                continue
            }
            ctx.processed.incrementAndGet()
            if (ctx.reached(current.pos)) {
                ctx.publish(reconstruct(current))
                return
            }
            expand(ctx, current, kind, access)
            ctx.finish()
        }
    }

    private fun expand(ctx: EtherwarpContext, current: EtherwarpNode, kind: EtherwarpKind, access: EtherwarpWorldAccess) {
        val eye = Vec3(current.x, current.y + kind.eyeHeight(), current.z)
        val rc = ctx.raycasts
        val seen = legacySeen.get().also { it.clear() }
        val candidateSeen = legacyCandidates.get().also { it.next(rc.size) }

        fun visit(index: Int) {
            if (!candidateSeen.add(index)) return
            val hit = kind.hit(eye, rc.dx[index], rc.dy[index], rc.dz[index], ctx.goal, access) ?: return
            if (!ctx.landingPolicy.accepts(hit) || !seen.add(hit.asLong())) return
            val aim = kind.resolveAim(eye, hit, ctx.range, rc.yaws[index], rc.pitches[index]) ?: return
            val g = current.g + kind.hopCost(hit)
            val h = heuristic(VecUtils.distance(hit, ctx.goal), ctx.range, ctx.hWeight)
            ctx.offer(EtherwarpNode(hit.x + 0.5, kind.landingY(hit.y), hit.z + 0.5, hit, g, h, current, aim.yaw, aim.pitch))
        }
        val toGoalX = ctx.goal.x + 0.5 - current.x
        val toGoalY = ctx.goal.y - current.y
        val toGoalZ = ctx.goal.z + 0.5 - current.z
        val goalYaw = yawOf(toGoalX, toGoalZ)
        val goalPitch = pitchOf(toGoalX, toGoalY, toGoalZ)
        val cone = (RayCone.SPHERE_DEG / max(1.0, length(toGoalX, toGoalY, toGoalZ) / ctx.range)).coerceAtLeast(MIN_CONE_DEG)
        val parent = current.parent
        val backYaw = parent?.let { yawOf(it.x - current.x, it.z - current.z) }
        val backPitch = parent?.let { pitchOf(it.x - current.x, it.y - current.y, it.z - current.z) }
        SearchCandidateOrder.visitLegacy(rc, goalYaw, goalPitch, cone, backYaw, backPitch, BACKTRACK_CONE_DEG, ::visit)
    }

    private fun yawOf(x: Double, z: Double): Double = Math.toDegrees(atan2(-x, z))

    private fun pitchOf(x: Double, y: Double, z: Double): Double {
        val length = length(x, y, z)
        return if (length <= 0.0) 0.0 else Math.toDegrees(asin((-y / length).coerceIn(-1.0, 1.0)))
    }

    private fun length(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    fun smooth(path: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, cache: BlockCache.SnapshotView? = null): List<EtherwarpNode> {
        return EtherwarpPathSmoother.smooth(path, range, kind, cache)
    }

    internal fun minimumHopRouteIndices(size: Int, canReach: (Int, Int) -> Boolean): List<Int> =
        EtherwarpPathSmoother.minimumHopRouteIndices(size, canReach)

    fun revalidateLive(path: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView? = null): List<EtherwarpNode>? {
        if (kind != EtherwarpKind.ETHERWARP || path.size < 2) return path
        val access = EtherwarpUtils.liveOrCachedAccess(snapshot) ?: return path
        val valid = mutableListOf(path[0])
        for (i in 0 until path.size - 1) {
            if (!revalidateEtherwarpHop(valid, path, i, kind, range, access, snapshot)) return null
        }
        return valid
    }

    private fun reconstruct(node: EtherwarpNode): List<EtherwarpNode> =
        EtherwarpRouteAimNormalizer.normalize(collect(node, mutableListOf()).asReversed())

    private tailrec fun collect(node: EtherwarpNode?, acc: MutableList<EtherwarpNode>): MutableList<EtherwarpNode> {
        if (node == null) return acc
        acc.add(node)
        return collect(node.parent, acc)
    }

    private fun heuristic(distance: Double, range: Double, weight: Double): Double {
        val hops = distance / range
        return (ceil(hops) + hops * HOP_TIE_BREAK) * weight
    }
    private const val INITIAL_SEEN_CAPACITY = 256
    private const val HOP_TIE_BREAK = 0.001
    private const val ZERO_ANGLE = 0f
}
