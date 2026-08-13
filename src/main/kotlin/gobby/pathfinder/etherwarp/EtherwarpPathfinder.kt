package gobby.pathfinder.etherwarp

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import gobby.features.dungeons.RoomPathfinder
import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.utils.skyblock.EtherwarpWorldAccess
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object EtherwarpPathfinder {

    private const val MIN_CONE_DEG = 60.0
    private const val MID_CONE_FACTOR = 2.0
    private const val BACKTRACK_CONE_DEG = 45.0
    private const val MID_STRIDE = 2
    private const val COARSE_STRIDE = 3
    private const val MAX_LANDING_RISE = 4

    fun findPath(
        from: Vec3,
        to: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig = EtherwarpPathConfig()
    ): List<EtherwarpNode>? {
        val cache = BlockCache.freeze()
        return search(from, to, kind, config, cache = cache)?.let { smooth(it, kind.searchRange(config), kind, cache) }
    }

    fun search(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig = EtherwarpPathConfig(),
        reached: (BlockPos) -> Boolean = { it == goal },
        cache: BlockCache.SnapshotView? = null,
        deadline: SearchDeadline = SearchDeadline(config.timeout)
    ): List<EtherwarpNode>? {
        val access = EtherwarpUtils.cachedAccess(cache) ?: return null
        return searchWith(from, goal, kind, config, reached, access, deadline)
    }

    fun searchWith(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        access: EtherwarpWorldAccess,
        deadline: SearchDeadline = SearchDeadline(config.timeout)
    ): List<EtherwarpNode>? {
        val range = kind.searchRange(config)
        val raycasts = RaycastCache.get(range, config.pitchStep, config.yawStep)
        val start = BlockPos.containing(from)
        val maxLandingY = if (kind.sneak) max(goal.y, start.y) + MAX_LANDING_RISE else Int.MAX_VALUE
        val ctx = EtherwarpContext(goal, range, config.hWeight, raycasts, deadline, reached, maxLandingY)
        ctx.offer(EtherwarpNode(from.x, from.y, from.z, start, 0.0, heuristic(VecUtils.distance(start, goal), range, config.hWeight), null, 0f, 0f))
        val workers = List(config.threads - 1) { thread { worker(ctx, kind, access) } }
        worker(ctx, kind, access)
        workers.forEach { it.join() }
        if (RoomPathfinder.pathDebug) {
            println("[GobbyPath] search processed=${ctx.processed.get()} elapsed=${ctx.elapsed}ms timedOut=${ctx.timedOut} hops=${ctx.result?.size ?: -1} range=$range")
        }
        return ctx.result
    }

    private fun worker(ctx: EtherwarpContext, kind: EtherwarpKind, access: EtherwarpWorldAccess) {
        while (!ctx.solved) {
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
        val seen = LongOpenHashSet()

        fun visit(index: Int) {
            val hit = kind.hit(eye, rc.dx[index], rc.dy[index], rc.dz[index], ctx.goal, access) ?: return
            if (hit.y > ctx.maxLandingY || !seen.add(hit.asLong())) return
            val aim = kind.resolveAim(eye, hit, ctx.range, rc.yaws[index], rc.pitches[index]) ?: return
            val g = current.g + 1.0 + kind.landingCost(hit)
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
        val backYaw = parent?.let { yawOf(it.x - current.x, it.z - current.z) } ?: 0.0
        val backPitch = parent?.let { pitchOf(it.x - current.x, it.y - current.y, it.z - current.z) } ?: 0.0

        for (band in 0 until rc.bandCount) {
            if (ctx.solved || ctx.expired) return
            val slots = rc.bandSize[band]
            val step = rc.bandYawStep[band]
            val first = rc.bandStart[band]
            val pitch = rc.bandPitch[band]
            val goalCenter = RayCone.slotOf(goalYaw, step, slots)
            val innerSpan = spanFor(pitch, goalPitch, cone, step, slots)
            val midSpan = spanFor(pitch, goalPitch, min(cone * MID_CONE_FACTOR, RayCone.SPHERE_DEG), step, slots)
            val backCenter = if (parent == null) 0 else RayCone.slotOf(backYaw, step, slots)
            val backSpan = if (parent == null) -1 else spanFor(pitch, backPitch, BACKTRACK_CONE_DEG, step, slots)

            fun visitSlot(rawSlot: Int) {
                val slot = ((rawSlot % slots) + slots) % slots
                if (!RayCone.contains(slot, backCenter, backSpan, slots)) visit(first + slot)
            }

            (-innerSpan..innerSpan).forEach { visitSlot(goalCenter + it) }
            var offset = -midSpan
            while (offset <= midSpan) {
                if (offset < -innerSpan || offset > innerSpan) visitSlot(goalCenter + offset)
                offset += MID_STRIDE
            }
            var slot = band % COARSE_STRIDE
            while (slot < slots) {
                if (!RayCone.contains(slot, goalCenter, midSpan, slots)) visitSlot(slot)
                slot += COARSE_STRIDE
            }
        }
    }

    private fun spanFor(bandPitch: Float, targetPitch: Double, coneDeg: Double, yawStep: Float, slots: Int): Int =
        RayCone.yawHalfWidth(bandPitch, targetPitch, coneDeg)
            .takeIf { it != RayCone.OUTSIDE }
            ?.let { RayCone.spanOf(it, yawStep, slots) } ?: -1

    private fun yawOf(x: Double, z: Double): Double = Math.toDegrees(atan2(-x, z))

    private fun pitchOf(x: Double, y: Double, z: Double): Double {
        val length = length(x, y, z)
        return if (length <= 0.0) 0.0 else Math.toDegrees(asin((-y / length).coerceIn(-1.0, 1.0)))
    }

    private fun length(x: Double, y: Double, z: Double): Double = sqrt(x * x + y * y + z * z)

    fun smooth(path: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, cache: BlockCache.SnapshotView? = null): List<EtherwarpNode> {
        if (path.size < 2) return path
        val smoothed = mutableListOf<EtherwarpNode>()
        var i = 0
        while (i < path.size - 1) {
            val current = path[i]
            val from = Vec3(current.x, current.y + kind.eyeHeight(), current.z)
            val furthest = (path.size - 1 downTo i + 1).first { it == i + 1 || VecUtils.centerDistanceSq(from, path[it].pos) <= range * range }
            val reach = (furthest downTo i + 1).firstNotNullOfOrNull { j ->
                kind.aimAt(from, path[j].pos, range, snapshot = cache)?.let { j to it }
            }
            val (next, aim) = reach ?: (i + 1 to Aim(path[i + 1].yaw, path[i + 1].pitch))
            smoothed.add(EtherwarpNode(current.x, current.y, current.z, current.pos, current.g, current.h, current.parent, aim.yaw, aim.pitch))
            i = next
        }
        smoothed.add(path.last())
        return smoothed
    }

    fun revalidateLive(path: List<EtherwarpNode>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView? = null): List<EtherwarpNode>? {
        if (kind != EtherwarpKind.ETHERWARP || path.size < 2) return path
        val access = EtherwarpUtils.liveOrCachedAccess(snapshot) ?: return path
        val valid = mutableListOf(path[0])
        for (i in 0 until path.size - 1) {
            val cur = valid.last()
            val eye = Vec3(cur.x, cur.y + kind.eyeHeight(), cur.z)
            val target = path[i + 1].pos
            val storedAim = Aim(cur.yaw, cur.pitch)
            val aim = EtherwarpUtils.validateAim(eye, target, range, storedAim.yaw to storedAim.pitch, access)
                .takeIf { it != EtherwarpUtils.EtherPos.NONE }?.let { storedAim }
                ?: EtherwarpUtils.aimForBlock(target, eye, range, access)?.let { Aim(it.first, it.second) }
            if (aim == null) {
                if (RoomPathfinder.pathDebug) {
                    println("[GobbyPath] revalidation failed hop=${i + 1}${EtherwarpUtils.hopDiagnostic(eye, target, storedAim.yaw to storedAim.pitch, range, snapshot)}")
                    println("[GobbyPath]   offsets ${EtherwarpUtils.explainAimFailure(eye, target, range, snapshot)}")
                }
                return null
            }
            cur.yaw = aim.yaw
            cur.pitch = aim.pitch
            valid.add(path[i + 1])
        }
        return valid
    }

    private fun reconstruct(node: EtherwarpNode): List<EtherwarpNode> = collect(node, mutableListOf()).asReversed()

    private tailrec fun collect(node: EtherwarpNode?, acc: MutableList<EtherwarpNode>): MutableList<EtherwarpNode> {
        if (node == null) return acc
        acc.add(node)
        return collect(node.parent, acc)
    }

    private fun heuristic(distance: Double, range: Double, weight: Double): Double = distance / range * weight
}

internal object RaycastCache {
    private val cache = ConcurrentHashMap<Triple<Double, Float, Float>, Raycasts>()

    fun get(range: Double, pitchStep: Float, yawStep: Float): Raycasts =
        cache.getOrPut(Triple(range, pitchStep, yawStep)) { Raycasts.generate(pitchStep, yawStep, range) }
}
