package gobby.utils

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.ArmorStandEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.floor

object BowSimulator {

    const val SHORTBOW_VELOCITY = 3.0
    const val PEARL_VELOCITY = 1.5
    const val ARROW_GRAVITY = 0.05
    const val PEARL_GRAVITY = 0.03
    const val DRAG = 0.99

    data class Outcome(val trail: List<Vec3d>, val impact: Vec3d?, val hitBlock: BlockPos?, val hitEntity: Entity?)

    fun simulate(start: Vec3d, vel0: Vec3d, gravity: Double, ticks: Int, checkEntities: Boolean = false): Outcome {
        val world = mc.world ?: return Outcome(emptyList(), null, null, null)
        val trail = ArrayList<Vec3d>(ticks + 1)
        trail += start
        var x = start.x; var y = start.y; var z = start.z
        var vx = vel0.x; var vy = vel0.y; var vz = vel0.z
        repeat(ticks) {
            val nx = x + vx; val ny = y + vy; val nz = z + vz
            val from = Vec3d(x, y, z); val to = Vec3d(nx, ny, nz)
            val blockHit = walkVoxels(world, from, to)
            val entityHit = if (checkEntities) nearestEntityHit(world, from, to) else null
            val pickBlock: Boolean = when {
                blockHit == null -> false
                entityHit == null -> true
                else -> from.squaredDistanceTo(blockHit.point) < from.squaredDistanceTo(entityHit.point)
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

    private data class BlockSegmentHit(val point: Vec3d, val pos: BlockPos)
    private data class EntitySegmentHit(val point: Vec3d, val entity: Entity)

    private fun walkVoxels(world: ClientWorld, from: Vec3d, to: Vec3d): BlockSegmentHit? {
        val sx = from.x; val sy = from.y; val sz = from.z
        val ex = to.x; val ey = to.y; val ez = to.z
        val dx = ex - sx; val dy = ey - sy; val dz = ez - sz
        var ix = floor(sx).toInt(); var iy = floor(sy).toInt(); var iz = floor(sz).toInt()
        val gx = floor(ex).toInt(); val gy = floor(ey).toInt(); val gz = floor(ez).toInt()

        val cursor = BlockPos.Mutable(ix, iy, iz)
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

    private fun segmentHitsShape(world: ClientWorld, pos: BlockPos.Mutable, from: Vec3d, to: Vec3d): Vec3d? {
        val state = world.getBlockState(pos)
        if (state.isAir) return null
        val shape = state.getCollisionShape(world, pos)
        if (shape.isEmpty) return null
        return shape.raycast(from, to, pos)?.pos
    }

    private fun nearestEntityHit(world: ClientWorld, from: Vec3d, to: Vec3d): EntitySegmentHit? {
        val player = mc.player
        var best: EntitySegmentHit? = null
        var bestDistSq = Double.MAX_VALUE
        for (entity in world.getOtherEntities(player, Box(from, to)) {
            it.isAlive && it !is ArmorStandEntity && it !is PersistentProjectileEntity
        }) {
            val opt = entity.boundingBox.raycast(from, to)
            if (opt.isEmpty) continue
            val hitPoint = opt.get()
            val d = from.squaredDistanceTo(hitPoint)
            if (d < bestDistSq) { bestDistSq = d; best = EntitySegmentHit(hitPoint, entity) }
        }
        return best
    }
}
