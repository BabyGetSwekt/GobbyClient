package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.utils.VecUtils
import gobby.utils.skyblock.EtherwarpUtils
import gobby.pathfinder.world.VoxelRay
import gobby.utils.rotation.AngleUtils.calcAimAnglesBetween
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object EtherwarpRaycaster {

    private const val MAX_STEPS = 1000
    private const val RANGE_MARGIN = 2.0
    private const val MIN_AIM_LENGTH = 1.0E-4
    private const val RAY_EPSILON = 1.0E-9

    private const val AXIS_X = 0
    private const val AXIS_Y = 1
    private const val AXIS_Z = 2
    private const val FACE_CENTER = 0.5
    private val SURFACE_SAMPLES = doubleArrayOf(FACE_CENTER, 0.15, 0.85)

    private class AimFace(private val axis: Int, private val value: Double) {
        val points: List<Triple<Double, Double, Double>> =
            SURFACE_SAMPLES.flatMap { u -> SURFACE_SAMPLES.map { v -> at(u, v) } }

        private fun at(u: Double, v: Double): Triple<Double, Double, Double> = when (axis) {
            AXIS_X -> Triple(value, u, v)
            AXIS_Y -> Triple(u, value, v)
            else -> Triple(u, v, value)
        }

        fun facesToward(dx: Double, dy: Double, dz: Double): Boolean = when (axis) {
            AXIS_X -> (value - FACE_CENTER) * dx > 0.0
            AXIS_Y -> (value - FACE_CENTER) * dy > 0.0
            else -> (value - FACE_CENTER) * dz > 0.0
        }
    }

    private val AIM_FACES = listOf(
        AimFace(AXIS_Y, 1.0),
        AimFace(AXIS_X, 0.0), AimFace(AXIS_X, 1.0), AimFace(AXIS_Z, 0.0), AimFace(AXIS_Z, 1.0),
        AimFace(AXIS_Y, 0.0)
    )

    fun transmission(eye: Vec3, ray: Vec3, aabbsAt: (BlockPos) -> List<AABB> = BlockCache::getShapeAabbs): BlockPos? {
        val dda = VoxelRay.threadLocal(eye, ray)
        val cursor = BlockPos.MutableBlockPos()
        var lastX = 0; var lastY = 0; var lastZ = 0; var hasLast = false
        repeat(MAX_STEPS) {
            if (blocked(cursor, dda.x, dda.y, dda.z, eye, ray, aabbsAt)) return if (hasLast) BlockPos(lastX, lastY, lastZ) else null
            if (dda.atEnd) return BlockPos(dda.x, dda.y, dda.z)
            lastX = dda.x; lastY = dda.y; lastZ = dda.z; hasLast = true
            dda.advance()
        }
        return null
    }

    fun aim(from: Vec3, to: BlockPos, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView? = null): Aim? {
        if (VecUtils.centerDistanceSq(from, to) > (range + RANGE_MARGIN) * (range + RANGE_MARGIN)) return null
        val dx = from.x - (to.x + FACE_CENTER)
        val dy = from.y - (to.y + FACE_CENTER)
        val dz = from.z - (to.z + FACE_CENTER)
        return AIM_FACES.asSequence()
            .filter { it.facesToward(dx, dy, dz) }
            .flatMap { it.points.asSequence() }
            .firstNotNullOfOrNull { tryAim(from, to, it, range, kind, snapshot) }
    }

    private fun tryAim(from: Vec3, to: BlockPos, offset: Triple<Double, Double, Double>, range: Double, kind: EtherwarpKind, snapshot: BlockCache.SnapshotView?): Aim? {
        val point = Vec3(to.x + offset.first, to.y + offset.second, to.z + offset.third)
        val dir = point.subtract(from)
        val length = dir.length()
        if (length < MIN_AIM_LENGTH) return null
        val r = dir.scale(range / length)
        val access = EtherwarpUtils.cachedAccess(snapshot) ?: return null
        val hit = kind.hit(from, r.x, r.y, r.z, to, access)
        return if (hit == to) calcAimAnglesBetween(from, point).let { Aim(it.first, it.second) } else null
    }

    private fun blocked(cursor: BlockPos.MutableBlockPos, x: Int, y: Int, z: Int, eye: Vec3, ray: Vec3, aabbsAt: (BlockPos) -> List<AABB>): Boolean =
        rayHitsBlock(cursor, x, y, z, eye, ray, aabbsAt) || rayHitsBlock(cursor, x, y + 1, z, eye, ray, aabbsAt)

    private fun rayHitsBlock(cursor: BlockPos.MutableBlockPos, x: Int, y: Int, z: Int, eye: Vec3, ray: Vec3, aabbsAt: (BlockPos) -> List<AABB>): Boolean =
        aabbsAt(cursor.set(x, y, z)).any {
            intersects(eye, ray, x + it.minX, y + it.minY, z + it.minZ, x + it.maxX, y + it.maxY, z + it.maxZ)
        }

    internal fun intersects(eye: Vec3, ray: Vec3, minX: Double, minY: Double, minZ: Double, maxX: Double, maxY: Double, maxZ: Double): Boolean {
        var tMin = 0.0
        var tMax = 1.0
        fun axis(origin: Double, direction: Double, lo: Double, hi: Double): Boolean {
            if (abs(direction) < RAY_EPSILON) return origin in lo..hi
            val inv = 1.0 / direction
            val t1 = (lo - origin) * inv
            val t2 = (hi - origin) * inv
            tMin = max(tMin, min(t1, t2))
            tMax = min(tMax, max(t1, t2))
            return tMax >= tMin
        }
        return axis(eye.x, ray.x, minX, maxX) && axis(eye.y, ray.y, minY, maxY) && axis(eye.z, ray.z, minZ, maxZ)
    }

}
