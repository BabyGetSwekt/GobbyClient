package gobby.pathfinder.etherwarp

import gobby.pathfinder.search.IntSearchHeap
import gobby.pathfinder.search.IntGenerationSet
import gobby.pathfinder.search.PrimitiveSearchArena
import gobby.pathfinder.search.SearchCandidateOrder
import gobby.pathfinder.search.SearchExpansionWork
import gobby.pathfinder.navigation.PlannerMemo
import gobby.pathfinder.navigation.PlannerMemoScope
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

internal object PrimitiveEtherwarpSearch {
    private val workspaces = ThreadLocal.withInitial(::Workspace)

    fun preload() {
        workspaces.get().reset()
    }

    fun find(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline,
        landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL
    ): PrimitiveSearchResult = search(from, goal, kind, config, reached, snapshot, deadline, landingFilter)

    private fun search(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline,
        landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL
    ): PrimitiveSearchResult {
        val access = EtherwarpUtils.cachedAccess(snapshot) ?: return PrimitiveSearchResult(null, PrimitiveSearchTermination.UNAVAILABLE)
        val state = initialize(from, goal, kind, config, landingFilter)
        return searchLoop(state, goal, kind, config, reached, snapshot, deadline, access)
    }

    private fun initialize(from: Vec3, goal: BlockPos, kind: EtherwarpKind, config: EtherwarpPathConfig, landingFilter: (BlockPos) -> Boolean = LandingPolicy.ACCEPT_ALL): SearchState {
        val range = kind.searchRange(config)
        val start = BlockPos.containing(from)
        val workspace = workspaces.get().also { it.reset() }
        val policy = LandingPolicy.upTo(maxLandingY(kind, start.y, goal.y), landingFilter)
        val startIndex = workspace.arena.add(start.asLong(), from.x, from.y, from.z, 0.0, heuristic(start, goal, range, config.hWeight), -1, 0f, 0f)
        workspace.heap.add(startIndex, workspace.arena)
        workspace.best.put(start.asLong(), 0.0)
        return SearchState(range, RaycastCache.get(range, config.pitchStep, config.yawStep), policy, workspace, workspace.pending)
    }

    private fun searchLoop(
        state: SearchState,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline,
        access: gobby.utils.skyblock.EtherwarpWorldAccess
    ): PrimitiveSearchResult {
        var preferFrontier = true
        while ((state.workspace.heap.size > 0 || state.pending.isNotEmpty()) && !deadline.expired && !Thread.currentThread().isInterrupted) {
            val frontier = state.workspace.heap.size > 0 && (state.pending.isEmpty() || preferFrontier)
            val work = if (frontier) null else state.pending.removeFirst()
            val current = work?.nodeIndex ?: state.workspace.heap.removeFirst(state.workspace.arena)
            val result = expand(state, current, work, frontier, goal, kind, config, reached, snapshot, deadline, access)
            if (result == null) return PrimitiveSearchResult(null, PrimitiveSearchTermination.MISSING_CHUNKS)
            if (result.isNotEmpty()) return PrimitiveSearchResult(result, PrimitiveSearchTermination.FOUND)
            preferFrontier = !frontier
        }
        val termination = when {
            Thread.currentThread().isInterrupted -> PrimitiveSearchTermination.INTERRUPTED
            deadline.expired -> PrimitiveSearchTermination.TIMED_OUT
            else -> PrimitiveSearchTermination.FRONTIER_EXHAUSTED
        }
        return PrimitiveSearchResult(null, termination)
    }

    private fun expand(
        state: SearchState,
        current: Int,
        work: SearchExpansionWork?,
        frontier: Boolean,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        snapshot: BlockCache.SnapshotView,
        deadline: SearchDeadline,
        access: gobby.utils.skyblock.EtherwarpWorldAccess
    ): List<EtherwarpNode>? {
        if (state.workspace.arena.g[current] > state.workspace.best.get(state.workspace.arena.positions[current])) return emptyList()
        val currentPos = BlockPos.of(state.workspace.arena.positions[current])
        if (reached(currentPos)) return publish(state.workspace.arena, current, snapshot)
        val eye = Vec3(state.workspace.arena.x[current], state.workspace.arena.y[current] + kind.eyeHeight(), state.workspace.arena.z[current])
        if (frontier) {
            exactGoal(current, eye, goal, kind, config, state.range, state.landingPolicy, reached, snapshot, access, state.workspace)?.let {
                return publish(state.workspace.arena, it, snapshot)
            }
        }
        val currentWork = work ?: createWork(state.rays, current, eye, goal, state.range, state.workspace)
        val solved = consumeCandidates(currentWork, current, currentPos, eye, goal, kind, config, state.range, state.landingPolicy, reached, state.rays, snapshot, access, state.workspace, deadline)
        if (solved >= 0) return publish(state.workspace.arena, solved, snapshot)
        if (!currentWork.done) state.pending.addLast(currentWork)
        return emptyList()
    }

    private fun createWork(rays: Raycasts, current: Int, eye: Vec3, goal: BlockPos, range: Double, workspace: Workspace): SearchExpansionWork {
        val (goalYaw, goalPitch) = aimAngles(eye, goal)
        val cone = (RayCone.SPHERE_DEG / max(1.0, sqrt(VecUtils.centerDistanceSq(eye, goal)) / range)).coerceAtLeast(MIN_CONE_DEG)
        return SearchExpansionWork(current, workspace.nextLandingGeneration(), SearchCandidateOrder.ordered(rays, goalYaw, goalPitch, cone, workspace.candidateOrderSeen))
    }

    private fun exactGoal(
        current: Int,
        eye: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        range: Double,
        landingPolicy: LandingPolicy,
        reached: (BlockPos) -> Boolean,
        snapshot: BlockCache.SnapshotView,
        access: gobby.utils.skyblock.EtherwarpWorldAccess,
        workspace: Workspace
    ): Int? {
        val memo = PlannerMemoScope.current()
        val key = PlannerMemo.exactAimKey(snapshot, eye, goal, range, kind.ordinal)
        val aim = if (memo == null) {
            kind.aimAt(eye, goal, range, snapshot = snapshot)
        } else {
            memo.exactAim(key) { kind.aimAt(eye, goal, range, snapshot = snapshot) }
        } ?: return null
        val direction = EtherwarpUtils.directionFromAngles(aim.yaw, aim.pitch)
        val hit = kind.hit(eye, direction.x * range, direction.y * range, direction.z * range, goal, access) ?: return null
        if (!landingPolicy.accepts(hit) || !reached(hit)) return null
        return offer(workspace, current, hit, aim, goal, range, config.hWeight, kind)
    }

    private fun consumeCandidates(
        work: SearchExpansionWork,
        current: Int,
        currentPos: BlockPos,
        eye: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        range: Double,
        landingPolicy: LandingPolicy,
        reached: (BlockPos) -> Boolean,
        rays: Raycasts,
        snapshot: BlockCache.SnapshotView,
        access: gobby.utils.skyblock.EtherwarpWorldAccess,
        workspace: Workspace,
        deadline: SearchDeadline
    ): Int {
        var solved = -1
        val memo = PlannerMemoScope.current()
        work.consume(CANDIDATE_BATCH_SIZE, { !deadline.expired && !Thread.currentThread().isInterrupted }) { index ->
            val compute = {
                workspace.rayMemo.resolve(currentPos.asLong(), index) {
                    kind.hit(eye, rays.dx[index], rays.dy[index], rays.dz[index], goal, access)
                }
            }
            val hit = if (memo == null) compute() else memo.ray(
                PlannerMemo.rayKey(snapshot, currentPos.asLong(), goal.asLong(), index, kind.ordinal),
                compute
            )
            if (hit == null) return@consume false
            if (!landingPolicy.accepts(hit) || !workspace.markLanding(hit.asLong(), work.landingGeneration)) return@consume false
            val aim = kind.resolveAim(eye, hit, range, rays.yaws[index], rays.pitches[index]) ?: return@consume false
            val child = offer(workspace, current, hit, aim, goal, range, config.hWeight, kind)
            if (child >= 0 && reached(hit)) solved = child
            solved >= 0
        }
        return solved
    }

    private fun offer(workspace: Workspace, parent: Int, hit: BlockPos, aim: Aim, goal: BlockPos, range: Double, hWeight: Double, kind: EtherwarpKind): Int {
        val cost = workspace.arena.g[parent] + kind.hopCost(hit)
        if (cost >= workspace.best.get(hit.asLong())) return -1
        val index = workspace.arena.add(hit.asLong(), hit.x + 0.5, kind.landingY(hit.y), hit.z + 0.5, cost, cost + heuristic(hit, goal, range, hWeight), parent, aim.yaw, aim.pitch)
        workspace.best.put(hit.asLong(), cost)
        workspace.heap.add(index, workspace.arena)
        return index
    }

    private fun publish(arena: PrimitiveSearchArena, goalIndex: Int, snapshot: BlockCache.SnapshotView): List<EtherwarpNode>? =
        PrimitiveRouteMaterializer.materialize(arena, goalIndex).takeIf { snapshot.missingChunksAccessed().isEmpty() }

    private fun aimAngles(from: Vec3, goal: BlockPos): Pair<Double, Double> {
        val x = goal.x + 0.5 - from.x
        val y = goal.y - from.y
        val z = goal.z + 0.5 - from.z
        return yawOf(x, z) to pitchOf(x, y, z)
    }

    private fun heuristic(from: BlockPos, goal: BlockPos, range: Double, weight: Double): Double {
        val hops = VecUtils.distance(from, goal) / range
        return (ceil(hops) + hops * HOP_TIE_BREAK) * weight
    }

    private fun yawOf(x: Double, z: Double): Double = Math.toDegrees(atan2(-x, z))

    private fun pitchOf(x: Double, y: Double, z: Double): Double = Math.toDegrees(asin((-y / sqrt(x * x + y * y + z * z)).coerceIn(-1.0, 1.0)))

    private class Workspace {
        val arena = PrimitiveSearchArena()
        val heap = IntSearchHeap()
        val best = Long2DoubleOpenHashMap().apply { defaultReturnValue(Double.POSITIVE_INFINITY) }
        private val landings = Long2IntOpenHashMap().apply { defaultReturnValue(0) }
        val rayMemo = RaycastMemo()
        val pending = ArrayDeque<SearchExpansionWork>()
        val candidateOrderSeen = IntGenerationSet()
        private var generation = 1

        fun reset() {
            arena.reset()
            heap.reset()
            best.clear()
            landings.clear()
            rayMemo.reset()
            pending.clear()
        }

        fun nextLandingGeneration(): Int {
            generation++
            if (generation == Int.MAX_VALUE) {
                landings.clear()
                generation = 1
            }
            return generation
        }

        fun markLanding(key: Long, generation: Int): Boolean = landings.put(key, generation) != generation
    }
    private data class SearchState(
        val range: Double,
        val rays: Raycasts,
        val landingPolicy: LandingPolicy,
        val workspace: Workspace,
        val pending: ArrayDeque<SearchExpansionWork>
    )

    private const val HOP_TIE_BREAK = 0.001
    private const val MIN_CONE_DEG = 50.0
    private const val CANDIDATE_BATCH_SIZE = 96
}
