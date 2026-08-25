package gobby.features.dungeons.puzzles

import gobby.utils.Utils.getBlockAtPos
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.tiles.Room
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks

internal object IceFillSolver {
    private val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private const val PACK_SHIFT = 16
    private const val PACK_MASK = 0xFFFF

    fun buildMask(floor: IceFill.Floor, room: Room, world: ClientLevel): Long {
        var mask = floor.bit(floor.start.x, floor.start.z) or floor.bit(floor.exit.x, floor.exit.z)
        val width = floor.xMax - floor.xMin + 1
        val height = floor.zMax - floor.zMin + 1
        repeat(width * height) { index ->
            val x = floor.xMin + index % width
            val z = floor.zMin + index / width
            if (world.getBlockAtPos(room.getRealCoords(BlockPos(x, floor.y, z))) == Blocks.AIR) mask = mask or floor.bit(x, z)
        }
        return mask
    }

    fun solve(floor: IceFill.Floor, iceMask: Long): List<BlockPos>? =
        Search(floor, iceMask).run()

    private class Search(private val floor: IceFill.Floor, private val iceMask: Long) {
        private val total = iceMask.countOneBits()
        private val exitBit = floor.bit(floor.exit.x, floor.exit.z)
        private val output = mutableListOf(BlockPos(floor.start.x, floor.y, floor.start.z))

        fun run(): List<BlockPos>? = output.takeIf {
            search(floor.start.x, floor.start.z, floor.bit(floor.start.x, floor.start.z), null)
        }

        private fun bitAt(x: Int, z: Int): Long =
            if (x in floor.xMin..floor.xMax && z in floor.zMin..floor.zMax) floor.bit(x, z) else 0L

        private fun runLength(x: Int, z: Int, direction: Pair<Int, Int>, filled: Long): Int {
            var length = 0
            var nextX = x + direction.first
            var nextZ = z + direction.second
            while (true) {
                val bit = bitAt(nextX, nextZ)
                if (bit == 0L || iceMask and bit == 0L || filled and bit != 0L) return length
                length++
                nextX += direction.first
                nextZ += direction.second
            }
        }

        private fun reachableFrom(startX: Int, startZ: Int, filled: Long): Long {
            val unfilled = iceMask and filled.inv()
            if (unfilled == 0L) return 0L
            return Reachability(floor, unfilled).find(startX, startZ)
        }

        private fun search(x: Int, z: Int, filled: Long, lastDirection: Pair<Int, Int>?): Boolean {
            if (filled.countOneBits() == total) return x == floor.exit.x && z == floor.exit.z
            val moves = movesFrom(x, z, filled, lastDirection)
            return moves.any { move -> tryMove(x, z, filled, move) }
        }

        private fun movesFrom(x: Int, z: Int, filled: Long, lastDirection: Pair<Int, Int>?) =
            directions.mapNotNull { direction ->
                val bit = bitAt(x + direction.first, z + direction.second)
                if (bit == 0L || iceMask and bit == 0L || filled and bit != 0L) null
                else Move(direction, bit, runLength(x, z, direction, filled))
            }.sortedWith(compareBy({ it.direction != lastDirection }, { -it.runLength }))

        private fun tryMove(x: Int, z: Int, filled: Long, move: Move): Boolean {
            val newFilled = filled or move.bit
            if (move.bit and exitBit != 0L && newFilled.countOneBits() < total) return false
            val nextX = x + move.direction.first
            val nextZ = z + move.direction.second
            val unfilled = iceMask and newFilled.inv()
            if (unfilled != 0L && reachableFrom(nextX, nextZ, newFilled) != unfilled) return false
            output.add(BlockPos(nextX, floor.y, nextZ))
            if (search(nextX, nextZ, newFilled, move.direction)) return true
            output.removeAt(output.lastIndex)
            return false
        }
    }

    private class Reachability(private val floor: IceFill.Floor, private val unfilled: Long) {
        private val directions = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private var visited = 0L
        private val stack = ArrayDeque<Int>()

        fun find(startX: Int, startZ: Int): Long {
            addNeighbors(startX, startZ)
            while (stack.isNotEmpty()) {
                val packed = stack.removeLast()
                addNeighbors(unpackX(packed), unpackZ(packed))
            }
            return visited
        }

        private fun addNeighbors(x: Int, z: Int) {
            directions.forEach { direction ->
                val nextX = x + direction.first
                val nextZ = z + direction.second
                val bit = bitAt(nextX, nextZ)
                if (bit != 0L && unfilled and bit != 0L && visited and bit == 0L) {
                    visited = visited or bit
                    stack.addLast(IceFillSolver.pack(nextX, nextZ))
                }
            }
        }

        private fun bitAt(x: Int, z: Int): Long =
            if (x in floor.xMin..floor.xMax && z in floor.zMin..floor.zMax) floor.bit(x, z) else 0L
    }

    private data class Move(val direction: Pair<Int, Int>, val bit: Long, val runLength: Int)

    private fun pack(x: Int, z: Int): Int = (x shl PACK_SHIFT) or (z and PACK_MASK)

    private fun unpackX(packed: Int): Int = packed shr PACK_SHIFT

    private fun unpackZ(packed: Int): Int = packed.toShort().toInt()
}
