package gobby.utils.rotation

import gobby.Gobbyclient.Companion.mc
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.Render3DEvent
import gobby.events.render.renderTickCounter
import gobby.utils.BowSimulator
import gobby.utils.PlayerUtils.getEyePosition
import gobby.utils.rotation.AngleUtils.calcAimAngles
import gobby.utils.timer.Clock
import net.minecraft.world.entity.Entity
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

object RotationUtils {

    val isEasing: Boolean get() = easing
    val isAimLocked: Boolean get() = aimLockTarget != null
    private var aimLockTarget: Entity? = null
    private var easing = false
    private var onComplete: (() -> Unit)? = null
    private var startYaw = 0f
    private var startPitch = 0f
    private var targetYaw = 0f
    private var targetPitch = 0f
    private val easeClock = Clock()
    private var entryYaw = 0f
    private var entryPitch = 0f
    private var lastYaw = 0f
    private var lastPitch = 0f
    private var duration = 0L
    private var easingFn: (Float) -> Float = ::easeInOutCubic

    val isAngleLocked: Boolean get() = angleLock != null
    private var angleLock: (() -> Pair<Float, Float>?)? = null
    private var lockSpeed = 200L
    private var lockArrival = 0.3f
    private val lockClock = Clock()
    private const val LOCK_REF_ANGLE = 90f
    private const val LOCK_MAX_FRAME_MS = 100L
    private const val FULL_TURN = 360f

    fun startAngleLock(durationMs: Long, arrival: Float = 0.3f, supplier: () -> Pair<Float, Float>?) {
        lockSpeed = durationMs.coerceAtLeast(1L)
        lockArrival = arrival.coerceIn(0.02f, 1f)
        angleLock = supplier
        lockClock.update()
        easing = false
    }

    fun stopAngleLock() { angleLock = null }

    fun startAimLock(entity: Entity) {
        aimLockTarget = entity
        easing = false
    }

    fun stopAimLock() {
        aimLockTarget = null
    }

    fun snapTo(yaw: Float, pitch: Float, serverSide: Boolean = false) {
        easing = false
        onComplete = null
        val player = mc.player ?: return
        if (serverSide) {
            val continuousYaw = nearestEquivalentYaw(yaw, player.yRot)
            mc.connection?.send(ServerboundMovePlayerPacket.Rot(continuousYaw, pitch, player.onGround(), player.horizontalCollision))
        } else {
            player.yRot = yaw
            player.xRot = pitch
        }
    }

    fun nearestEquivalentYaw(targetYaw: Float, referenceYaw: Float): Float =
        targetYaw + (Math.rint(((referenceYaw - targetYaw) / FULL_TURN).toDouble()) * FULL_TURN).toFloat()

    fun easeToBlock(pos: BlockPos, timeMs: Long, ease: (Float) -> Float = ::easeInOutCubic, onComplete: (() -> Unit)? = null) {
        easeToVec(Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5), timeMs, ease, onComplete)
    }

    fun easeToVec(target: Vec3, timeMs: Long, ease: (Float) -> Float = ::easeInOutCubic, onComplete: (() -> Unit)? = null) {
        val (yaw, pitch) = calcAimAngles(target) ?: return
        easeTo(yaw, pitch, timeMs, ease, onComplete)
    }

    fun easeTo(yaw: Float, pitch: Float, timeMs: Long, ease: (Float) -> Float = ::easeInOutCubic, onComplete: (() -> Unit)? = null) {
        val player = mc.player ?: return
        startYaw = player.yRot
        startPitch = player.xRot
        targetYaw = startYaw + wrapDelta(yaw - startYaw)
        targetPitch = pitch.coerceIn(-90f, 90f)
        entryYaw = startYaw + wrapDelta(lastYaw - startYaw)
        entryPitch = lastPitch
        easeClock.update()
        duration = timeMs
        easingFn = ease
        this.onComplete = onComplete
        easing = true
    }

    fun easeTowards(yaw: Float, pitch: Float, yawFactor: Float = 0.18f, pitchFactor: Float = 0.12f) {
        easing = false
        onComplete = null
        val player = mc.player ?: return
        player.yRot += wrapDelta(yaw - player.yRot) * yawFactor.coerceIn(0.01f, 1f)
        player.xRot += (pitch.coerceIn(-90f, 90f) - player.xRot) * pitchFactor.coerceIn(0.01f, 1f)
    }

    private fun wrapDelta(delta: Float): Float {
        var d = delta % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun linear(t: Float): Float = t

    fun easeOutCubic(t: Float): Float = 1f - (1f - t).let { it * it * it }

    fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

    @SubscribeEvent
    fun onRender(event: Render3DEvent) {
        if (event.type != Render3DEvent.Type.BeforeEntity) return
        val player = mc.player ?: return
        lastYaw = player.yRot
        lastPitch = player.xRot
        if (renderAimLock(player, event) || renderAngleLock(player)) return
        renderEasing(player)
    }

    private fun renderAimLock(player: net.minecraft.world.entity.player.Player, event: Render3DEvent): Boolean {
        val target = aimLockTarget ?: return false
        if (!target.isAlive || target.isRemoved()) {
            aimLockTarget = null
            return true
        }
        val delta = event.renderTickCounter.getGameTimeDeltaPartialTick(false)
        val targetPosition = Vec3(
            target.xOld + (target.x - target.xOld) * delta,
            target.yOld + (target.y - target.yOld) * delta + target.bbHeight * 0.5,
            target.zOld + (target.z - target.zOld) * delta
        )
        val (yaw, pitch) = calcAimAngles(targetPosition) ?: return true
        player.yRot += wrapDelta(yaw - player.yRot) * 0.15f
        player.xRot += (pitch - player.xRot).coerceIn(-90f, 90f) * 0.15f
        return true
    }

    private fun renderAngleLock(player: net.minecraft.world.entity.player.Player): Boolean {
        val supplier = angleLock ?: return false
        val delta = lockClock.getTime().coerceIn(1L, LOCK_MAX_FRAME_MS)
        lockClock.update()
        supplier()?.let { (yaw, pitch) ->
            val maxStep = LOCK_REF_ANGLE * delta.toFloat() / lockSpeed.toFloat()
            player.yRot += (wrapDelta(yaw - player.yRot) * lockArrival).coerceIn(-maxStep, maxStep)
            player.xRot += ((pitch.coerceIn(-90f, 90f) - player.xRot) * lockArrival).coerceIn(-maxStep, maxStep)
        }
        return true
    }

    private fun renderEasing(player: net.minecraft.world.entity.player.Player) {
        if (!easing) return
        val elapsed = easeClock.getTime()
        if (elapsed >= duration) {
            player.yRot = targetYaw
            player.xRot = targetPitch
            easing = false
            onComplete?.invoke()
            onComplete = null
            return
        }
        val progress = easingFn((elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
        player.yRot = CatmullRom.settle(entryYaw, startYaw, targetYaw, progress)
        player.xRot = CatmullRom.settle(entryPitch, startPitch, targetPitch, progress)
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        stopAimLock()
        stopAngleLock()
        easing = false
        onComplete = null
    }

    private const val SIM_TICKS = 120
    private const val AIM_GRID_RES = 5
    private const val AIM_FACE_MARGIN = 0.05
    private const val AIM_ITERATIONS = 15
    private const val AIM_STEP_CLAMP = 25f
    private const val AIM_PITCH_LIMIT = 88f
    private const val AIM_PITCH_EPSILON = 1f
    private const val AIM_SLOPE_EPSILON = 1e-4
    private const val AIM_TOLERANCE = 0.02

    fun aimAtBlockShortbow(pos: BlockPos, rotate: Boolean = true, delayMs: Long = 150L, onComplete: (() -> Unit)? = null) {
        val (yaw, pitch) = findClearShortbowAim(pos) ?: return
        if (rotate) easeTo(yaw, pitch, delayMs, onComplete = onComplete)
        else { snapTo(yaw, pitch); onComplete?.invoke() }
    }

    fun canAimShortbow(pos: BlockPos): Boolean = findClearShortbowAim(pos) != null

    private data class AimCandidate(val point: Vec3, val angles: Pair<Float, Float>?, val valid: Boolean)

    private fun findClearShortbowAim(target: BlockPos): Pair<Float, Float>? {
        val eye = getEyePosition() ?: return null
        val grid = buildVisibleAimGrid(target, eye, AIM_GRID_RES)
        val cands = grid.map { (ox, oy, oz) ->
            val point = Vec3(target.x + ox, target.y + oy, target.z + oz)
            val angles = computeShortbowAim(point)
            val valid = angles != null && shortbowFirstHit(eye, angles.first, angles.second) == target
            AimCandidate(point, angles, valid)
        }
        val valid = cands.filter { it.valid }
        if (valid.isEmpty()) return null
        val cx = valid.sumOf { it.point.x } / valid.size
        val cy = valid.sumOf { it.point.y } / valid.size
        val cz = valid.sumOf { it.point.z } / valid.size
        val centroid = Vec3(cx, cy, cz)
        return valid.minByOrNull { it.point.distanceToSqr(centroid) }?.angles
    }

    private fun buildVisibleAimGrid(target: BlockPos, eye: Vec3, n: Int): List<Triple<Double, Double, Double>> {
        val lo = AIM_FACE_MARGIN
        val hi = 1.0 - AIM_FACE_MARGIN
        val out = ArrayList<Triple<Double, Double, Double>>(n * n * n)
        for (i in 0 until n)
            for (j in 0 until n)
                for (k in 0 until n) {
                    val x = sampleCoord(lo, hi, n, i)
                    val y = sampleCoord(lo, hi, n, j)
                    val z = sampleCoord(lo, hi, n, k)
                    out += Triple(x, y, z)
                }
        return out.sortedBy {
            val dx = it.first - 0.5
            val dy = it.second - 0.5
            val dz = it.third - 0.5
            dx * dx + dy * dy + dz * dz
        }
    }

    private fun sampleCoord(lo: Double, hi: Double, n: Int, i: Int): Double =
        if (n == 1) (lo + hi) * 0.5 else lo + (hi - lo) * (i / (n - 1.0))

    private fun shortbowFirstHit(eye: Vec3, yaw: Float, pitch: Float): BlockPos? {
        val dir = AngleUtils.directionFromAngles(yaw, pitch)
        return BowSimulator.simulate(eye, dir.scale(BowSimulator.SHORTBOW_VELOCITY), BowSimulator.ARROW_GRAVITY, SIM_TICKS).hitBlock
    }

    private fun computeShortbowAim(target: Vec3): Pair<Float, Float>? {
        val eye = getEyePosition() ?: return null
        val dx = target.x - eye.x
        val dz = target.z - eye.z
        val horizDist = sqrt(dx * dx + dz * dz)
        if (horizDist < 1e-4) return null
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        var pitch = (-Math.toDegrees(atan2(target.y - eye.y, horizDist))).toFloat().coerceIn(-AIM_PITCH_LIMIT, AIM_PITCH_LIMIT)
        repeat(AIM_ITERATIONS) {
            val arrowY = simulateArrowYAtRange(eye, yaw, pitch, horizDist) ?: return null
            val yMiss = target.y - arrowY
            if (abs(yMiss) < AIM_TOLERANCE) return yaw to pitch
            val probeY = simulateArrowYAtRange(eye, yaw, pitch + AIM_PITCH_EPSILON, horizDist) ?: return null
            val slope = (probeY - arrowY) / AIM_PITCH_EPSILON
            if (abs(slope) < AIM_SLOPE_EPSILON) return null
            val step = (yMiss / slope).coerceIn(-AIM_STEP_CLAMP.toDouble(), AIM_STEP_CLAMP.toDouble())
            pitch = (pitch + step).toFloat().coerceIn(-AIM_PITCH_LIMIT, AIM_PITCH_LIMIT)
        }
        return yaw to pitch
    }

    private fun simulateArrowYAtRange(eye: Vec3, yaw: Float, pitch: Float, targetHorizDist: Double): Double? {
        val dir = AngleUtils.directionFromAngles(yaw, pitch)
        var vx = dir.x * BowSimulator.SHORTBOW_VELOCITY
        var vy = dir.y * BowSimulator.SHORTBOW_VELOCITY
        var vz = dir.z * BowSimulator.SHORTBOW_VELOCITY
        var x = eye.x
        var y = eye.y
        var z = eye.z
        var prevX: Double
        var prevY: Double
        var prevZ: Double
        repeat(SIM_TICKS) {
            prevX = x; prevY = y; prevZ = z
            x += vx; y += vy; z += vz
            val horiz = sqrt((x - eye.x) * (x - eye.x) + (z - eye.z) * (z - eye.z))
            if (horiz >= targetHorizDist) {
                val prevHoriz = sqrt((prevX - eye.x) * (prevX - eye.x) + (prevZ - eye.z) * (prevZ - eye.z))
                val span = horiz - prevHoriz
                val t = if (span > 1e-9) (targetHorizDist - prevHoriz) / span else 0.0
                return prevY + (y - prevY) * t
            }
            vx *= BowSimulator.DRAG; vy = vy * BowSimulator.DRAG - BowSimulator.ARROW_GRAVITY; vz *= BowSimulator.DRAG
        }
        return null
    }

    fun rotateByDirection(dir: Direction, x: Double, y: Double, z: Double): Vec3 = when (dir) {
        Direction.NORTH -> Vec3(x, y, z)
        Direction.EAST -> Vec3(-z, y, x)
        Direction.SOUTH -> Vec3(-x, y, -z)
        Direction.WEST -> Vec3(z, y, -x)
        else -> Vec3.ZERO
    }
}
