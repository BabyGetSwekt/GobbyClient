package gobby.pathfinder.solver

import gobby.pathfinder.world.BlockCache
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

internal object VoxelGroundSnapper {
    fun snap(pos: Vec3): VoxelGroundSolver.Node? {
        val centerX = floor(pos.x).toInt()
        val centerZ = floor(pos.z).toInt()
        var fallback: BlockCache.StandSurface? = null
        for (radius in 0..SNAP_SPIRAL_RADIUS) {
            val result = scanRadius(centerX, centerZ, radius, pos, fallback)
            fallback = result.fallback
            result.surface?.let { return node(it) }
        }
        return fallback?.let(::node)
    }

    private fun scanRadius(centerX: Int, centerZ: Int, radius: Int, pos: Vec3, fallback: BlockCache.StandSurface?): ScanResult {
        var localFallback = fallback
        var best: BlockCache.StandSurface? = null
        val side = radius * 2 + 1
        for (index in 0 until side * side) {
            val dx = index / side - radius
            val dz = index % side - radius
            if (max(abs(dx), abs(dz)) != radius) continue
            val surfaces = BlockCache.getStandableSurfaces(centerX + dx, centerZ + dz, pos.y - 2.5, pos.y + 1.5)
            val candidate = surfaces.minByOrNull { abs(it.feetY - pos.y) } ?: continue
            if (localFallback == null) localFallback = candidate
            if (radius > 0 && !reachable(pos, candidate)) continue
            if (best == null || abs(candidate.feetY - pos.y) < abs(best.feetY - pos.y)) best = candidate
        }
        return ScanResult(best, localFallback)
    }

    private fun reachable(from: Vec3, surface: BlockCache.StandSurface): Boolean {
        val passY = max(from.y, surface.feetY)
        val steps = max(2, (max(abs(surface.pos.x + 0.5 - from.x), abs(surface.pos.z + 0.5 - from.z)) * 2).toInt())
        return BlockCache.isSweepClear(from.x, from.z, surface.pos.x + 0.5, surface.pos.z + 0.5, steps) { passY }
    }

    private fun node(surface: BlockCache.StandSurface) = VoxelGroundSolver.Node(surface.pos.x, surface.pos.y, surface.pos.z, surface.feetY)
    private data class ScanResult(val surface: BlockCache.StandSurface?, val fallback: BlockCache.StandSurface?)
    private const val SNAP_SPIRAL_RADIUS = 2
}
