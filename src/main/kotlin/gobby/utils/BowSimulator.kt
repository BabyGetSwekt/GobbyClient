package gobby.utils

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
//? if <=1.21.10
import net.minecraft.world.entity.projectile.AbstractArrow
//? if >=1.21.11
/*import net.minecraft.world.entity.projectile.arrow.AbstractArrow*/
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
        var x = start.x; var y = start.y; var z = start.z
        var vx = vel0.x; var vy = vel0.y; var vz = vel0.z
        repeat(ticks) {
            val nx = x + vx; val ny = y + vy; val nz = z + vz
            val from = Vec3(x, y, z); val to = Vec3(nx, ny, nz)
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

    private fun walkVoxels(world: ClientLevel, from: Vec3, to: Vec3): BlockSegmentHit? {
        val sx = from.x; val sy = from.y; val sz = from.z
        val ex = to.x; val ey = to.y; val ez = to.z
        val dx = ex - sx; val dy = ey - sy; val dz = ez - sz
        var ix = floor(sx).toInt(); var iy = floor(sy).toInt(); var iz = floor(sz).toInt()
        val gx = floor(ex).toInt(); val gy = floor(ey).toInt(); val gz = floor(ez).toInt()

        val cursor = BlockPos.MutableBlockPos(ix, iy, iz)
        segmentHitsShape(world, cursor, from, to)?.let { return BlockSegmentHit(it, BlockPos(ix, iy, iz)) }

        val stepX = if (dx > 0) 1 else -1
        val stepY = if (dy > 0) 1 else -1
        val stepZ = if (dz > 0) 1 else -1
        val tdx = if (dx == 0.0) Double.POSITIVE_INFINITY else abs(1.0 / dx)
        val tdy = if (dy == 0.0) Double.POSITIVE_INFINITY else abs(1.0 / dy)
        val tdz = if (dz == 0.0) Double.POSITIVE_INFINITY else abs(1.0 / dz)
        var tmx = if (dx == 0.0) Double.POSITIVE_INFINITY else (if (dx > 0) (ix + 1 - sx) else (sx - ix)) * tdx
        var tmy = if (dy == 0.0) Double.POSITIVE_INFINITY else (if (dy > 0) (iy + 1 - sy) else (sy - iy)) * tdy
        var tmz = if (dz == 0.0) Double.POSITIVE_INFINITY else (if (dz > 0) (iz + 1 - sz) else (sz - iz)) * tdz

        val maxSteps = abs(gx - ix) + abs(gy - iy) + abs(gz - iz)
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
