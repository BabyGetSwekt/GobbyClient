package gobby.pathfinder.prediction

import gobby.pathfinder.world.BlockCache
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class JumpSimulation(
    val landing: Vec3?,
    val airTicks: Int,
    val apexY: Double,
    val tickPositions: List<Vec3>,
    val autoJumpChained: Boolean
)

object MovementSimulator {

    private const val GRAVITY = 0.08
    private const val VERTICAL_DRAG = 0.98
    private const val AIR_FRICTION = 0.91
    private const val GROUND_SLIPPERINESS = 0.6
    private const val GROUND_FRICTION = GROUND_SLIPPERINESS * 0.91
    private const val GROUND_ACCEL_MAGIC = 0.21600002
    private const val AIR_ACCEL = 0.02
    private const val SPRINT_AIR_ACCEL = 0.026
    private const val SPRINT_JUMP_BOOST = 0.2
    private const val INPUT_SCALE = 0.98
    private const val MAX_SIM_TICKS = 60
    private const val MIN_HEADING = 1.0E-4
    private const val CONTACT_EPSILON = 1.0E-5
    private const val AUTO_JUMP_PROBE_DIST = 0.8

    private class Body(var x: Double, var y: Double, var z: Double) {
        fun box(): AABB = AABB(
            x - BlockCache.PLAYER_HALF_WIDTH, y, z - BlockCache.PLAYER_HALF_WIDTH,
            x + BlockCache.PLAYER_HALF_WIDTH, y + BlockCache.PLAYER_HEIGHT, z + BlockCache.PLAYER_HALF_WIDTH
        )
    }

    private data class MoveResult(val hitX: Boolean, val hitZ: Boolean, val hitGround: Boolean, val hitCeiling: Boolean)

    fun simulateJump(
        pos: Vec3,
        velocity: Vec3,
        headingX: Double,
        headingZ: Double,
        sprinting: Boolean,
        jumpVelocity: Double,
        groundSpeedAttribute: Double,
        autoJump: Boolean,
        maxClimb: Double
    ): JumpSimulation {
        val headingLength = sqrt(headingX * headingX + headingZ * headingZ)
        if (headingLength < MIN_HEADING) return JumpSimulation(null, 0, pos.y, emptyList(), autoJumpChained = false)
        val nx = headingX / headingLength
        val nz = headingZ / headingLength

        var vx = velocity.x + if (sprinting) nx * SPRINT_JUMP_BOOST else 0.0
        var vz = velocity.z + if (sprinting) nz * SPRINT_JUMP_BOOST else 0.0
        var vy = jumpVelocity
        val groundAccel = groundSpeedAttribute * (GROUND_ACCEL_MAGIC / (GROUND_FRICTION * GROUND_FRICTION * GROUND_FRICTION)) * INPUT_SCALE
        val airAccel = (if (sprinting) SPRINT_AIR_ACCEL else AIR_ACCEL) * INPUT_SCALE

        val body = Body(pos.x, pos.y, pos.z)
        var grounded = true
        var apex = pos.y
        var chained = false
        val positions = ArrayList<Vec3>(MAX_SIM_TICKS)

        repeat(MAX_SIM_TICKS) { tick ->
            val wasGrounded = grounded
            val accel = if (wasGrounded) groundAccel else airAccel
            vx += nx * accel
            vz += nz * accel

            val result = collideMove(body, vx, vy, vz, allowStep = wasGrounded)
            if (body.y > apex) apex = body.y
            positions += Vec3(body.x, body.y, body.z)

            if (result.hitCeiling) vy = 0.0
            if (result.hitX) vx = 0.0
            if (result.hitZ) vz = 0.0
            grounded = result.hitGround && vy <= 0.0

            if (grounded && tick > 0) {
                val chainAutoJump = autoJump && (result.hitX || result.hitZ) && climbableAhead(body, nx, nz, maxClimb)
                if (!chainAutoJump) {
                    return JumpSimulation(Vec3(body.x, body.y, body.z), tick + 1, apex, positions, chained)
                }
                chained = true
                grounded = false
                vy = jumpVelocity
                if (sprinting) {
                    vx += nx * SPRINT_JUMP_BOOST
                    vz += nz * SPRINT_JUMP_BOOST
                }
            }

            vy = if (grounded) 0.0 else (vy - GRAVITY) * VERTICAL_DRAG
            val friction = if (wasGrounded) GROUND_FRICTION else AIR_FRICTION
            vx *= friction
            vz *= friction
        }
        return JumpSimulation(null, MAX_SIM_TICKS, apex, positions, chained)
    }

    private fun collideMove(body: Body, dx: Double, dy: Double, dz: Double, allowStep: Boolean): MoveResult {
        var box = body.box()
        val movedY = clipAxis(box, dy, Axis.Y)
        box = box.move(0.0, movedY, 0.0)

        var movedX: Double
        var movedZ: Double
        if (abs(dx) > abs(dz)) {
            movedX = clipAxis(box, dx, Axis.X)
            box = box.move(movedX, 0.0, 0.0)
            movedZ = clipAxis(box, dz, Axis.Z)
        } else {
            movedZ = clipAxis(box, dz, Axis.Z)
            box = box.move(0.0, 0.0, movedZ)
            movedX = clipAxis(box, dx, Axis.X)
        }

        val hitX = abs(movedX - dx) > CONTACT_EPSILON
        val hitZ = abs(movedZ - dz) > CONTACT_EPSILON

        if ((hitX || hitZ) && allowStep) {
            val stepped = tryStep(body, dx, dy, dz, movedX, movedZ)
            if (stepped != null) {
                body.x = stepped.x
                body.y = stepped.y
                body.z = stepped.z
                return MoveResult(hitX = false, hitZ = false, hitGround = true, hitCeiling = false)
            }
        }

        body.x += movedX
        body.y += movedY
        body.z += movedZ
        val hitGround = dy < 0.0 && movedY > dy + CONTACT_EPSILON
        val hitCeiling = dy > 0.0 && movedY < dy - CONTACT_EPSILON
        return MoveResult(hitX, hitZ, hitGround, hitCeiling)
    }

    private fun tryStep(body: Body, dx: Double, dy: Double, dz: Double, directX: Double, directZ: Double): Vec3? {
        var box = body.box()
        val lift = clipAxis(box, BlockCache.STEP_HEIGHT, Axis.Y)
        if (lift < CONTACT_EPSILON) return null
        box = box.move(0.0, lift, 0.0)
        val steppedX = clipAxis(box, dx, Axis.X)
        box = box.move(steppedX, 0.0, 0.0)
        val steppedZ = clipAxis(box, dz, Axis.Z)
        box = box.move(0.0, 0.0, steppedZ)
        val settle = clipAxis(box, -lift + min(dy, 0.0), Axis.Y)
        if (abs(steppedX) + abs(steppedZ) <= abs(directX) + abs(directZ) + CONTACT_EPSILON) return null
        return Vec3(body.x + steppedX, body.y + lift + settle, body.z + steppedZ)
    }

    private enum class Axis { X, Y, Z }

    private fun clipAxis(box: AABB, move: Double, axis: Axis): Double {
        if (move == 0.0) return 0.0
        var allowed = move
        val sweep = sweepBounds(box, move, axis)
        val cursor = BlockPos.MutableBlockPos()
        for (bx in floor(sweep.minX).toInt()..floor(sweep.maxX).toInt()) {
            for (by in floor(sweep.minY).toInt()..floor(sweep.maxY).toInt()) {
                for (bz in floor(sweep.minZ).toInt()..floor(sweep.maxZ).toInt()) {
                    for (shape in BlockCache.getShapeAabbs(cursor.set(bx, by, bz))) {
                        val world = shape.move(bx.toDouble(), by.toDouble(), bz.toDouble())
                        allowed = clipAgainst(box, world, allowed, axis)
                        if (abs(allowed) < CONTACT_EPSILON) return 0.0
                    }
                }
            }
        }
        return allowed
    }

    private fun sweepBounds(box: AABB, move: Double, axis: Axis): AABB = when (axis) {
        Axis.X -> AABB(box.minX + min(move, 0.0), box.minY, box.minZ, box.maxX + max(move, 0.0), box.maxY, box.maxZ)
        Axis.Y -> AABB(box.minX, box.minY + min(move, 0.0), box.minZ, box.maxX, box.maxY + max(move, 0.0), box.maxZ)
        Axis.Z -> AABB(box.minX, box.minY, box.minZ + min(move, 0.0), box.maxX, box.maxY, box.maxZ + max(move, 0.0))
    }

    private fun clipAgainst(box: AABB, other: AABB, allowed: Double, axis: Axis): Double {
        val overlaps = when (axis) {
            Axis.X -> box.minY < other.maxY - CONTACT_EPSILON && box.maxY > other.minY + CONTACT_EPSILON &&
                box.minZ < other.maxZ - CONTACT_EPSILON && box.maxZ > other.minZ + CONTACT_EPSILON
            Axis.Y -> box.minX < other.maxX - CONTACT_EPSILON && box.maxX > other.minX + CONTACT_EPSILON &&
                box.minZ < other.maxZ - CONTACT_EPSILON && box.maxZ > other.minZ + CONTACT_EPSILON
            Axis.Z -> box.minX < other.maxX - CONTACT_EPSILON && box.maxX > other.minX + CONTACT_EPSILON &&
                box.minY < other.maxY - CONTACT_EPSILON && box.maxY > other.minY + CONTACT_EPSILON
        }
        if (!overlaps) return allowed
        return when (axis) {
            Axis.X -> clipComponent(box.minX, box.maxX, other.minX, other.maxX, allowed)
            Axis.Y -> clipComponent(box.minY, box.maxY, other.minY, other.maxY, allowed)
            Axis.Z -> clipComponent(box.minZ, box.maxZ, other.minZ, other.maxZ, allowed)
        }
    }

    private fun clipComponent(boxMin: Double, boxMax: Double, otherMin: Double, otherMax: Double, allowed: Double): Double {
        if (allowed > 0.0 && boxMax <= otherMin + CONTACT_EPSILON) {
            return min(allowed, otherMin - boxMax)
        }
        if (allowed < 0.0 && boxMin >= otherMax - CONTACT_EPSILON) {
            return max(allowed, otherMax - boxMin)
        }
        return allowed
    }

    private fun climbableAhead(body: Body, nx: Double, nz: Double, maxClimb: Double): Boolean {
        val probeX = floor(body.x + nx * AUTO_JUMP_PROBE_DIST).toInt()
        val probeZ = floor(body.z + nz * AUTO_JUMP_PROBE_DIST).toInt()
        val surfaces = BlockCache.getStandableSurfaces(probeX, probeZ, body.y + CONTACT_EPSILON, body.y + maxClimb)
        return surfaces.isNotEmpty()
    }
}
