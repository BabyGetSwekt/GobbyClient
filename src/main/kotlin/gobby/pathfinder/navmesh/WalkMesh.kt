package gobby.pathfinder.navmesh

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor

class WalkMesh(val polygons: List<WalkPolygon>) {

    val isEmpty: Boolean get() = polygons.isEmpty()

    fun polygonContaining(target: Vec3): WalkPolygon? {
        val blockX = floor(target.x).toInt()
        val blockZ = floor(target.z).toInt()
        var bestMatch: WalkPolygon? = null
        var bestYDelta = Double.MAX_VALUE
        for (poly in polygons) {
            if (!poly.contains(blockX, blockZ)) continue
            val delta = abs(poly.surfaceY - target.y)
            if (delta < bestYDelta) {
                bestYDelta = delta
                bestMatch = poly
            }
        }
        return bestMatch
    }

    fun nearestPolygon(target: Vec3): WalkPolygon? =
        polygons.minByOrNull { it.nearestPointTo(target).distanceToSqr(target) }
}
