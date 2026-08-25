package gobby.utils

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.projectile.arrow.AbstractArrow
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

object BowSimulator {

    const val SHORTBOW_VELOCITY = 3.0
    const val PEARL_VELOCITY = 1.5
    const val ARROW_GRAVITY = 0.05
    const val PEARL_GRAVITY = 0.03
    const val DRAG = 0.99

    data class Outcome(val trail: List<Vec3>, val impact: Vec3?, val hitBlock: BlockPos?, val hitEntity: Entity?)

    fun simulate(start: Vec3, vel0: Vec3, gravity: Double, ticks: Int, checkEntities: Boolean = false): Outcome {
        val world = mc.level ?: return Outcome(emptyList(), null, null, null)
        val trail = ArrayList<Vec3>(ticks + 1)
        trail += start
        var x = start.x
        var y = start.y
        var z = start.z
        var vx = vel0.x
        var vy = vel0.y
        var vz = vel0.z
        repeat(ticks) {
            val nx = x + vx
            val ny = y + vy
            val nz = z + vz
            val from = Vec3(x, y, z)
            val to = Vec3(nx, ny, nz)
            val blockHit = walkVoxels(world, from, to)
            val entityHit = if (checkEntities) nearestEntityHit(world, from, to) else null
            val pickBlock: Boolean = when {
                blockHit == null -> false
                entityHit == null -> true
                else -> from.distanceToSqr(blockHit.point) < from.distanceToSqr(entityHit.point)
            }
            if (pickBlock && blockHit != null) {
                trail += blockHit.point
                return Outcome(trail, blockHit.point, blockHit.pos, null)
            }
            if (entityHit != null) {
                trail += entityHit.point
                return Outcome(trail, entityHit.point, null, entityHit.entity)
            }
            trail += to
            x = nx; y = ny; z = nz
            vx *= DRAG; vy = vy * DRAG - gravity; vz *= DRAG
        }
        return Outcome(trail, null, null, null)
    }

    private data class BlockSegmentHit(val point: Vec3, val pos: BlockPos)
    private data class EntitySegmentHit(val point: Vec3, val entity: Entity)

    private fun axisDelta(distance: Double, origin: Double, cell: Int): Pair<Double, Double> {
        if (distance == 0.0) return Double.POSITIVE_INFINITY to Double.POSITIVE_INFINITY
        val step = abs(1.0 / distance)
        val toBoundary = if (distance > 0) cell + 1 - origin else origin - cell
        return step to toBoundary * step
    }

    private fun walkVoxels(world: ClientLevel, from: Vec3, to: Vec3): BlockSegmentHit? {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dz = to.z - from.z
        var ix = floor(from.x).toInt()
        var iy = floor(from.y).toInt()
        var iz = floor(from.z).toInt()

        val cursor = BlockPos.MutableBlockPos(ix, iy, iz)
        segmentHitsShape(world, cursor, from, to)?.let { return BlockSegmentHit(it, BlockPos(ix, iy, iz)) }

        val stepX = if (dx > 0) 1 else -1
        val stepY = if (dy > 0) 1 else -1
        val stepZ = if (dz > 0) 1 else -1
        val (tdx, initialX) = axisDelta(dx, from.x, ix)
        val (tdy, initialY) = axisDelta(dy, from.y, iy)
        val (tdz, initialZ) = axisDelta(dz, from.z, iz)
        var tmx = initialX
        var tmy = initialY
        var tmz = initialZ

        val maxSteps = abs(floor(to.x).toInt() - ix) + abs(floor(to.y).toInt() - iy) + abs(floor(to.z).toInt() - iz)
        repeat(maxSteps) {
            when {
                tmx < tmy && tmx < tmz -> { ix += stepX; tmx += tdx }
                tmy < tmz -> { iy += stepY; tmy += tdy }
                else -> { iz += stepZ; tmz += tdz }
            }
            cursor.set(ix, iy, iz)
            segmentHitsShape(world, cursor, from, to)?.let { return BlockSegmentHit(it, BlockPos(ix, iy, iz)) }
        }
        return null
    }

    private fun segmentHitsShape(world: ClientLevel, pos: BlockPos.MutableBlockPos, from: Vec3, to: Vec3): Vec3? {
        val state = world.getBlockState(pos)
        if (state.isAir) return null
        val shape = state.getCollisionShape(world, pos)
        if (shape.isEmpty) return null
        return shape.clip(from, to, pos)?.location
    }

    private fun nearestEntityHit(world: ClientLevel, from: Vec3, to: Vec3): EntitySegmentHit? {
        val player = mc.player
        var best: EntitySegmentHit? = null
        var bestDistSq = Double.MAX_VALUE
        for (entity in world.getEntities(player, AABB(from, to)).filter { it.isAlive && it !is ArmorStand && it !is AbstractArrow }) {
            val opt = entity.boundingBox.clip(from, to)
            if (opt.isEmpty) continue
            val hitPoint = opt.get()
            val d = from.distanceToSqr(hitPoint)
            if (d < bestDistSq) { bestDistSq = d; best = EntitySegmentHit(hitPoint, entity) }
        }
        return best
    }
}
