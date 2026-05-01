package gobby.utils.rotation

import gobby.Gobbyclient.Companion.mc
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.render.NewRender3DEvent
import gobby.utils.BowSimulator
import gobby.utils.PlayerUtils.getEyePosition
import gobby.utils.rotation.AngleUtils.calcAimAngles
import gobby.utils.timer.Clock
import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
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
    private var duration = 0L

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
            mc.networkHandler?.sendPacket(PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, player.isOnGround, player.horizontalCollision))
        } else {
            player.yaw = yaw
            player.pitch = pitch
        }
    }

    fun easeToBlock(pos: BlockPos, timeMs: Long, onComplete: (() -> Unit)? = null) {
        easeToVec(Vec3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5), timeMs, onComplete)
    }

    fun easeToVec(target: Vec3d, timeMs: Long, onComplete: (() -> Unit)? = null) {
        val (yaw, pitch) = calcAimAngles(target) ?: return
        easeTo(yaw, pitch, timeMs, onComplete)
    }

    fun easeTo(yaw: Float, pitch: Float, timeMs: Long, onComplete: (() -> Unit)? = null) {
        val player = mc.player ?: return
        startYaw = player.yaw
        startPitch = player.pitch
        targetYaw = startYaw + wrapDelta(yaw - startYaw)
        targetPitch = pitch.coerceIn(-90f, 90f)
        easeClock.update()
        duration = timeMs
        this.onComplete = onComplete
        easing = true
    }

    private fun wrapDelta(delta: Float): Float {
        var d = delta % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    private fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }

    @SubscribeEvent
    fun onRender(event: NewRender3DEvent) {
        val player = mc.player ?: return

        val lockTarget = aimLockTarget
        if (lockTarget != null) {
            if (!lockTarget.isAlive || lockTarget.isRemoved) {
                aimLockTarget = null
            } else {
                val delta = event.renderTickCounter.getTickProgress(false)
                val tx = lockTarget.lastRenderX + (lockTarget.x - lockTarget.lastRenderX) * delta
                val ty = lockTarget.lastRenderY + (lockTarget.y - lockTarget.lastRenderY) * delta + lockTarget.height * 0.5
                val tz = lockTarget.lastRenderZ + (lockTarget.z - lockTarget.lastRenderZ) * delta
                val (yaw, pitch) = calcAimAngles(Vec3d(tx, ty, tz)) ?: return
                player.yaw += wrapDelta(yaw - player.yaw) * 0.15f
                player.pitch += (pitch - player.pitch).coerceIn(-90f, 90f) * 0.15f
            }
            return
        }

        if (!easing) return

        val elapsed = easeClock.getTime()
        if (elapsed >= duration) {
            player.yaw = targetYaw
            player.pitch = targetPitch
            easing = false
            onComplete?.invoke()
            onComplete = null
            return
        }

        val progress = easeInOutCubic((elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
        player.yaw = startYaw + (targetYaw - startYaw) * progress
        player.pitch = startPitch + (targetPitch - startPitch) * progress
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        stopAimLock()
        easing = false
        onComplete = null
    }

    private const val SIM_TICKS = 120
    private const val AIM_GRID_RES = 5
    private const val AIM_FACE_MARGIN = 0.05

    fun aimAtBlockShortbow(pos: BlockPos, rotate: Boolean = true, delayMs: Long = 150L, onComplete: (() -> Unit)? = null) {
        val (yaw, pitch) = findClearShortbowAim(pos) ?: return
        if (rotate) easeTo(yaw, pitch, delayMs, onComplete)
        else { snapTo(yaw, pitch); onComplete?.invoke() }
    }

    private data class AimCandidate(val point: Vec3d, val angles: Pair<Float, Float>?, val valid: Boolean)

    private fun findClearShortbowAim(target: BlockPos): Pair<Float, Float>? {
        val eye = getEyePosition() ?: return null
        val grid = buildVisibleAimGrid(target, eye, AIM_GRID_RES)
        val cands = grid.map { (ox, oy, oz) ->
            val point = Vec3d(target.x + ox, target.y + oy, target.z + oz)
            val angles = computeShortbowAim(point)
            val valid = angles != null && shortbowFirstHit(eye, angles.first, angles.second) == target
            AimCandidate(point, angles, valid)
        }
        val valid = cands.filter { it.valid }
        if (valid.isEmpty()) return null
        val cx = valid.sumOf { it.point.x } / valid.size
        val cy = valid.sumOf { it.point.y } / valid.size
        val cz = valid.sumOf { it.point.z } / valid.size
        val centroid = Vec3d(cx, cy, cz)
        return valid.minByOrNull { it.point.squaredDistanceTo(centroid) }?.angles
    }

    private fun buildVisibleAimGrid(target: BlockPos, eye: Vec3d, n: Int): List<Triple<Double, Double, Double>> {
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
            val dx = it.first - 0.5; val dy = it.second - 0.5; val dz = it.third - 0.5
            dx * dx + dy * dy + dz * dz
        }
    }

    private fun sampleCoord(lo: Double, hi: Double, n: Int, i: Int): Double =
        if (n == 1) (lo + hi) * 0.5 else lo + (hi - lo) * (i / (n - 1.0))

    private fun shortbowFirstHit(eye: Vec3d, yaw: Float, pitch: Float): BlockPos? {
        val dir = AngleUtils.directionFromAngles(yaw, pitch)
        return BowSimulator.simulate(eye, dir.multiply(BowSimulator.SHORTBOW_VELOCITY), BowSimulator.ARROW_GRAVITY, SIM_TICKS).hitBlock
    }

    private fun computeShortbowAim(target: Vec3d): Pair<Float, Float>? {
        val eye = getEyePosition() ?: return null
        val dx = target.x - eye.x
        val dz = target.z - eye.z
        val horizDist = sqrt(dx * dx + dz * dz)
        if (horizDist < 1e-4) return null
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        var pitch = (-Math.toDegrees(atan2(target.y - eye.y, horizDist))).toFloat()
        repeat(10) {
            val arrowY = simulateArrowYAtRange(eye, yaw, pitch, horizDist) ?: return null
            val yMiss = target.y - arrowY
            if (abs(yMiss) < 0.02) return@repeat
            pitch -= Math.toDegrees(atan2(yMiss, horizDist)).toFloat()
            if (pitch < -90f || pitch > 90f) return null
        }
        return yaw to pitch
    }

    private fun simulateArrowYAtRange(eye: Vec3d, yaw: Float, pitch: Float, targetHorizDist: Double): Double? {
        val dir = AngleUtils.directionFromAngles(yaw, pitch)
        var vx = dir.x * BowSimulator.SHORTBOW_VELOCITY
        var vy = dir.y * BowSimulator.SHORTBOW_VELOCITY
        var vz = dir.z * BowSimulator.SHORTBOW_VELOCITY
        var x = eye.x; var y = eye.y; var z = eye.z
        var prevX: Double; var prevY: Double; var prevZ: Double
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

    fun rotateByDirection(dir: Direction, x: Double, y: Double, z: Double): Vec3d = when (dir) {
        Direction.NORTH -> Vec3d(x, y, z)
        Direction.EAST -> Vec3d(-z, y, x)
        Direction.SOUTH -> Vec3d(-x, y, -z)
        Direction.WEST -> Vec3d(z, y, -x)
        else -> Vec3d.ZERO
    }
}
