package gobby.pathfinder

import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Cells {

    private const val X_BITS = 26
    private const val Y_BITS = 12
    private const val Z_BITS = 26
    private const val Z_MASK = (1L shl Z_BITS) - 1L
    private const val Y_MASK = (1L shl Y_BITS) - 1L
    private const val X_MASK = (1L shl X_BITS) - 1L

    val FACE_NEIGHBORS: IntArray = intArrayOf(
        1, 0, 0,
        -1, 0, 0,
        0, 1, 0,
        0, -1, 0,
        0, 0, 1,
        0, 0, -1
    )

    val ALL_26_NEIGHBORS: IntArray = run {
        val out = ArrayList<Int>(78)
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            if (dx == 0 && dy == 0 && dz == 0) continue
            out += dx; out += dy; out += dz
        }
        out.toIntArray()
    }

    fun pack(x: Int, y: Int, z: Int): Long =
        ((x.toLong() and X_MASK) shl (Y_BITS + Z_BITS)) or
                ((y.toLong() and Y_MASK) shl Z_BITS) or
                (z.toLong() and Z_MASK)

    fun pack(pos: BlockPos): Long = pack(pos.x, pos.y, pos.z)

    fun unpack(key: Long): BlockPos {
        val z = signExtend(key and Z_MASK, Z_BITS)
        val y = signExtend((key shr Z_BITS) and Y_MASK, Y_BITS)
        val x = signExtend((key shr (Y_BITS + Z_BITS)) and X_MASK, X_BITS)
        return BlockPos(x.toInt(), y.toInt(), z.toInt())
    }

    private fun signExtend(value: Long, bits: Int): Long {
        val signBit = 1L shl (bits - 1)
        return if (value and signBit != 0L) value or (Long.MIN_VALUE shr (63 - bits)) else value
    }

    fun octileHeuristic(ax: Int, ay: Int, az: Int, bx: Int, by: Int, bz: Int): Double {
        val dx = abs(ax - bx)
        val dy = abs(ay - by)
        val dz = abs(az - bz)
        val maxAxis = max(dx, max(dy, dz))
        val minAxis = min(dx, min(dy, dz))
        val midAxis = dx + dy + dz - maxAxis - minAxis
        val triCost = sqrt(3.0) - sqrt(2.0)
        val biCost = sqrt(2.0) - 1.0
        return minAxis * triCost + (midAxis - minAxis) * biCost + maxAxis
    }
}
