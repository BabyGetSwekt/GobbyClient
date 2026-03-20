package gobby.pathfinder.movement

import gobby.pathfinder.movement.InputManager.MoveAction
import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.world.BlockCache.StandSurface
import net.minecraft.util.math.BlockPos
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class Neighbor(
    val pos: BlockPos,
    val feetY: Double,
    val action: MoveAction,
    val cost: Double
)

object MovementSimulator {

    const val BASE_WALK_SPEED = 0.1

    private const val CARDINAL_COST = 1.0
    private const val DIAGONAL_COST = 1.41421356237
    private const val WALK_DROP_HEIGHT = 0.6
    private const val JUMP_COST = 1.65
    private const val JUMP_RISE_COST = 0.35
    private const val STEP_RISE_COST = 0.2
    private const val FALL_BASE_COST = 0.55
    private const val FALL_PER_BLOCK = 0.32
    private const val MAX_FALL_SCAN = 40
    private const val SWEEP_STEPS_PER_BLOCK = 8
    private const val JUMP_ASCENT_FRACTION = 0.35
    private const val JUMP_HORIZONTAL_DELAY = 0.08

    private val CARDINAL_DIRS = arrayOf(
        intArrayOf(0, 1),
        intArrayOf(0, -1),
        intArrayOf(-1, 0),
        intArrayOf(1, 0)
    )

    private val DIAGONAL_DIRS = arrayOf(
        intArrayOf(1, 1),
        intArrayOf(-1, 1),
        intArrayOf(1, -1),
        intArrayOf(-1, -1)
    )

    private fun speedFactor(playerSpeed: Double): Double {
        return max(playerSpeed / BASE_WALK_SPEED, 0.05)
    }

    private fun sweepSteps(horizontalDistance: Double): Int {
        return ceil(horizontalDistance * SWEEP_STEPS_PER_BLOCK).toInt().coerceAtLeast(6)
    }

    private fun centerX(pos: BlockPos): Double = pos.x + 0.5

    private fun centerZ(pos: BlockPos): Double = pos.z + 0.5

    private fun pathIsClear(steps: Int, sampleAt: (Double) -> Triple<Double, Double, Double>): Boolean {
        if (steps <= 0) return true
        for (i in 0..steps) {
            val t = i.toDouble() / steps.toDouble()
            val (x, feetY, z) = sampleAt(t)
            if (!BlockCache.isBodyClearAt(x, feetY, z)) return false
        }
        return true
    }

    private fun canWalkBetween(from: StandSurface, to: StandSurface): Boolean {
        val horizontalDistance = sqrt(
            (to.pos.x - from.pos.x).toDouble() * (to.pos.x - from.pos.x).toDouble() +
                (to.pos.z - from.pos.z).toDouble() * (to.pos.z - from.pos.z).toDouble()
        )
        val steps = sweepSteps(horizontalDistance)
        return pathIsClear(steps) { t ->
            Triple(
                centerX(from.pos) + (centerX(to.pos) - centerX(from.pos)) * t,
                from.feetY + (to.feetY - from.feetY) * t,
                centerZ(from.pos) + (centerZ(to.pos) - centerZ(from.pos)) * t
            )
        }
    }

    private fun jumpFeetY(fromFeetY: Double, toFeetY: Double, t: Double): Double {
        val peakY = fromFeetY + BlockCache.MAX_JUMP_RISE
        return if (t <= JUMP_ASCENT_FRACTION) {
            fromFeetY + (peakY - fromFeetY) * (t / JUMP_ASCENT_FRACTION)
        } else {
            val descendT = (t - JUMP_ASCENT_FRACTION) / (1.0 - JUMP_ASCENT_FRACTION)
            peakY + (toFeetY - peakY) * descendT
        }
    }

    private fun canJumpBetween(from: StandSurface, to: StandSurface): Boolean {
        val rise = to.feetY - from.feetY
        if (rise > BlockCache.MAX_JUMP_RISE) return false

        val horizontalDistance = sqrt(
            (to.pos.x - from.pos.x).toDouble() * (to.pos.x - from.pos.x).toDouble() +
                (to.pos.z - from.pos.z).toDouble() * (to.pos.z - from.pos.z).toDouble()
        )
        val steps = sweepSteps(horizontalDistance) + 2
        return pathIsClear(steps) { t ->
            val horizontalT = ((t - JUMP_HORIZONTAL_DELAY) / (1.0 - JUMP_HORIZONTAL_DELAY)).coerceIn(0.0, 1.0)
            Triple(
                centerX(from.pos) + (centerX(to.pos) - centerX(from.pos)) * horizontalT,
                jumpFeetY(from.feetY, to.feetY, t),
                centerZ(from.pos) + (centerZ(to.pos) - centerZ(from.pos)) * horizontalT
            )
        }
    }

    private fun canFallBetween(from: StandSurface, to: StandSurface): Boolean {
        val horizontalDistance = sqrt(
            (to.pos.x - from.pos.x).toDouble() * (to.pos.x - from.pos.x).toDouble() +
                (to.pos.z - from.pos.z).toDouble() * (to.pos.z - from.pos.z).toDouble()
        )
        val steps = sweepSteps(horizontalDistance) + 2
        return pathIsClear(steps) { t ->
            val feetY = if (t <= 0.35) {
                from.feetY
            } else {
                val fallT = (t - 0.35) / 0.65
                from.feetY + (to.feetY - from.feetY) * fallT
            }
            Triple(
                centerX(from.pos) + (centerX(to.pos) - centerX(from.pos)) * t,
                feetY,
                centerZ(from.pos) + (centerZ(to.pos) - centerZ(from.pos)) * t
            )
        }
    }

    private fun walkNeighbor(surface: StandSurface, baseCost: Double, rise: Double, speedFactor: Double): Neighbor {
        val risePenalty = if (rise > 0.0) rise * STEP_RISE_COST else abs(rise) * 0.04
        return Neighbor(surface.pos, surface.feetY, MoveAction.FORWARD, (baseCost + risePenalty) / speedFactor)
    }

    private fun jumpNeighbor(surface: StandSurface, rise: Double, speedFactor: Double): Neighbor {
        return Neighbor(
            surface.pos,
            surface.feetY,
            MoveAction.JUMP,
            (JUMP_COST + rise * JUMP_RISE_COST) / speedFactor
        )
    }

    private fun fallNeighbor(surface: StandSurface, baseCost: Double, fallDist: Double, speedFactor: Double): Neighbor {
        return Neighbor(
            surface.pos,
            surface.feetY,
            MoveAction.FORWARD,
            (baseCost + FALL_BASE_COST + fallDist * FALL_PER_BLOCK) / speedFactor
        )
    }

    private fun bestWalkSurface(current: StandSurface, x: Int, z: Int): StandSurface? {
        val minY = current.feetY - WALK_DROP_HEIGHT
        val maxY = current.feetY + BlockCache.STEP_HEIGHT
        return BlockCache.getStandableSurfaces(x, z, minY, maxY).firstOrNull { candidate ->
            val rise = candidate.feetY - current.feetY
            rise <= BlockCache.STEP_HEIGHT && canWalkBetween(current, candidate)
        }
    }

    private fun bestJumpSurface(current: StandSurface, x: Int, z: Int): StandSurface? {
        val minY = current.feetY + BlockCache.STEP_HEIGHT + 1.0E-3
        val maxY = current.feetY + BlockCache.MAX_JUMP_RISE
        return BlockCache.getStandableSurfaces(x, z, minY, maxY).firstOrNull { candidate ->
            canJumpBetween(current, candidate)
        }
    }

    private fun bestFallSurface(current: StandSurface, x: Int, z: Int): StandSurface? {
        val minY = current.feetY - MAX_FALL_SCAN
        val maxY = current.feetY - WALK_DROP_HEIGHT - 1.0E-3
        return BlockCache.getStandableSurfaces(x, z, minY, maxY).firstOrNull { candidate ->
            canFallBetween(current, candidate)
        }
    }

    private fun addCardinalNeighbors(current: StandSurface, dx: Int, dz: Int, speedFactor: Double, neighbors: MutableList<Neighbor>) {
        val x = current.pos.x + dx
        val z = current.pos.z + dz

        bestWalkSurface(current, x, z)?.let { surface ->
            neighbors.add(walkNeighbor(surface, CARDINAL_COST, surface.feetY - current.feetY, speedFactor))
        }

        bestJumpSurface(current, x, z)?.let { surface ->
            neighbors.add(jumpNeighbor(surface, surface.feetY - current.feetY, speedFactor))
        }

        bestFallSurface(current, x, z)?.let { surface ->
            neighbors.add(fallNeighbor(surface, CARDINAL_COST, current.feetY - surface.feetY, speedFactor))
        }
    }

    private fun addDiagonalNeighbor(current: StandSurface, dx: Int, dz: Int, speedFactor: Double, neighbors: MutableList<Neighbor>) {
        val diagX = current.pos.x + dx
        val diagZ = current.pos.z + dz

        val first = bestWalkSurface(current, current.pos.x + dx, current.pos.z)
        val second = bestWalkSurface(current, current.pos.x, current.pos.z + dz)
        if (first == null && second == null) return

        val candidate = BlockCache.getStandableSurfaces(
            diagX,
            diagZ,
            current.feetY - WALK_DROP_HEIGHT,
            current.feetY + BlockCache.STEP_HEIGHT
        ).firstOrNull { surface ->
            val rise = surface.feetY - current.feetY
            rise <= BlockCache.STEP_HEIGHT &&
                canWalkBetween(current, surface) &&
                (first != null || second != null)
        } ?: return

        neighbors.add(walkNeighbor(candidate, DIAGONAL_COST, candidate.feetY - current.feetY, speedFactor))
    }

    fun getNeighbors(pos: BlockPos, feetY: Double, playerSpeed: Double): List<Neighbor> {
        val speedFactor = speedFactor(playerSpeed)
        val neighbors = mutableListOf<Neighbor>()
        val current = StandSurface(pos, feetY)

        for (dir in CARDINAL_DIRS) {
            addCardinalNeighbors(current, dir[0], dir[1], speedFactor, neighbors)
        }

        for (dir in DIAGONAL_DIRS) {
            addDiagonalNeighbor(current, dir[0], dir[1], speedFactor, neighbors)
        }

        return neighbors
    }
}
