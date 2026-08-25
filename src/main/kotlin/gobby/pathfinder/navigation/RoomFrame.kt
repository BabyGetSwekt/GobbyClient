package gobby.pathfinder.navigation

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.dungeon.map.MapGrid
import gobby.utils.skyblock.dungeon.map.MapTile
import gobby.utils.skyblock.dungeon.tiles.RoomData
import gobby.utils.skyblock.dungeon.tiles.Rotations
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.block.Blocks
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class LocalLanding(val x: Int, val y: Int, val z: Int)
data class RoomFrameCell(val x: Int, val z: Int, val topY: Int)

data class RoomFrame(val clay: BlockPos, val rotation: Rotations) {
    fun toLocal(world: BlockPos): LocalLanding {
        val dx = world.x - clay.x
        val dz = world.z - clay.z
        val (x, z) = when (rotation) {
            Rotations.SOUTH -> dx to dz
            Rotations.NORTH -> -dx to -dz
            Rotations.WEST -> dz to -dx
            Rotations.EAST -> -dz to dx
            Rotations.NONE -> error(INVALID_ROTATION)
        }
        return LocalLanding(x, world.y - clay.y, z)
    }

    fun toWorld(local: LocalLanding): BlockPos {
        val (dx, dz) = when (rotation) {
            Rotations.SOUTH -> local.x to local.z
            Rotations.NORTH -> -local.x to -local.z
            Rotations.WEST -> -local.z to local.x
            Rotations.EAST -> local.z to -local.x
            Rotations.NONE -> error(INVALID_ROTATION)
        }
        return BlockPos(clay.x + dx, clay.y + local.y, clay.z + dz)
    }

    fun toLocal(world: Vec3): Vec3 {
        val (x, z) = rotateToLocal(world.x - clay.x, world.z - clay.z)
        return Vec3(x, world.y - clay.y, z)
    }

    fun toWorld(local: Vec3): Vec3 {
        val (x, z) = rotateToWorld(local.x, local.z)
        return Vec3(clay.x + x, clay.y + local.y, clay.z + z)
    }

    fun toLocal(world: AABB): AABB = transformBox(world, ::toLocal)

    fun toWorld(local: AABB): AABB = transformBox(local, ::toWorld)

    fun toWorldYaw(localYaw: Float): Float = wrapYaw(localYaw + yawOffset())

    fun toLocalYaw(worldYaw: Float): Float = wrapYaw(worldYaw - yawOffset())

    private fun yawOffset(): Float = when (rotation) {
        Rotations.SOUTH -> 0f
        Rotations.NORTH -> 180f
        Rotations.WEST -> 90f
        Rotations.EAST -> -90f
        Rotations.NONE -> error(INVALID_ROTATION)
    }

    private fun rotateToLocal(x: Double, z: Double): Pair<Double, Double> = when (rotation) {
        Rotations.SOUTH -> x to z
        Rotations.NORTH -> -x to -z
        Rotations.WEST -> z to -x
        Rotations.EAST -> -z to x
        Rotations.NONE -> error(INVALID_ROTATION)
    }

    private fun rotateToWorld(x: Double, z: Double): Pair<Double, Double> = when (rotation) {
        Rotations.SOUTH -> x to z
        Rotations.NORTH -> -x to -z
        Rotations.WEST -> -z to x
        Rotations.EAST -> z to -x
        Rotations.NONE -> error(INVALID_ROTATION)
    }

    private fun transformBox(box: AABB, transform: (Vec3) -> Vec3): AABB {
        val first = transform(Vec3(box.minX, box.minY, box.minZ))
        val second = transform(Vec3(box.maxX, box.maxY, box.maxZ))
        return AABB(min(first.x, second.x), min(first.y, second.y), min(first.z, second.z), max(first.x, second.x), max(first.y, second.y), max(first.z, second.z))
    }

    private fun wrapYaw(yaw: Float): Float {
        val wrapped = yaw - floor((yaw + FULL_TURN) / FULL_TURN) * FULL_TURN
        return if (wrapped == NEGATIVE_HALF_TURN) HALF_TURN else wrapped
    }
}

object RoomFrameLocator {
    fun locate(data: RoomData, component: Set<Int>, grid: Array<MapTile>, snapshot: BlockCache.SnapshotView): RoomFrame? {
        val cells = component.asSequence().sorted().mapNotNull { cell ->
            val tile = grid.getOrNull(cell) as? MapTile.Room ?: return@mapNotNull null
            if (tile.data.name != data.name || tile.data.shape != data.shape) return@mapNotNull null
            val x = MapGrid.worldX(MapGrid.col(cell))
            val z = MapGrid.worldZ(MapGrid.row(cell))
            RoomFrameCell(x, z, topLayer(x, z, snapshot))
        }.toList()
        if (cells.size != component.size || cells.any { it.topY == EMPTY_TOP }) return null
        val ordered = cells.sortedWith(compareBy<RoomFrameCell>({ it.z }, { it.x }))
        if (data.name == FAIRY_ROOM) return RoomFrame(BlockPos(ordered.first().x - ROOM_OFFSET, ordered.first().topY, ordered.first().z - ROOM_OFFSET), Rotations.SOUTH)
        val blue = Blocks.DYED_TERRACOTTA.blue()
        val rotations = Rotations.entries.filter { it != Rotations.NONE }
        val attempts = rotations.size * ordered.size
        return (0 until attempts).asSequence().map { index ->
            rotations[index / ordered.size] to ordered[index % ordered.size]
        }.firstNotNullOfOrNull { (rotation, cell) ->
            val marker = BlockPos(cell.x + rotation.x, cell.topY, cell.z + rotation.z)
            marker.takeIf { snapshot.getBlockState(it)?.block == blue && markerClear(marker, component.size, snapshot) }
                ?.let { RoomFrame(it, rotation) }
        }
    }

    private fun topLayer(x: Int, z: Int, snapshot: BlockCache.SnapshotView): Int {
        val surfaceY = snapshot.blockYRange(x, z)?.last?.minus(1)
        if (surfaceY != null) {
            val state = snapshot.getBlockState(BlockPos(x, surfaceY, z))
            if (state != null && !state.isAir) return if (state.block == Blocks.GOLD_BLOCK) surfaceY - 1 else surfaceY
        }
        for (y in MAX_SCAN_Y downTo MIN_SCAN_Y) {
            val state = snapshot.getBlockState(BlockPos(x, y, z)) ?: continue
            if (!state.isAir) return if (state.block == Blocks.GOLD_BLOCK) y - 1 else y
        }
        return EMPTY_TOP
    }

    private fun markerClear(marker: BlockPos, componentSize: Int, snapshot: BlockCache.SnapshotView): Boolean =
        componentSize == 1 || Direction.entries.asSequence().filter { it.axis.isHorizontal }.all {
            snapshot.getBlockState(marker.relative(it))?.block in setOf(Blocks.AIR, Blocks.DYED_TERRACOTTA.blue())
        }

}

private const val ROOM_OFFSET = 15
private const val MAX_SCAN_Y = 160
private const val MIN_SCAN_Y = 12
private const val EMPTY_TOP = 0
private const val FULL_TURN = 360f
private const val HALF_TURN = 180f
private const val NEGATIVE_HALF_TURN = -180f
private const val FAIRY_ROOM = "Fairy"
private const val INVALID_ROTATION = "A room frame cannot use NONE rotation"
