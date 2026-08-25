package gobby.pathfinder.navmesh

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicInteger

class WalkPolygon(
    val minX: Int,
    val minZ: Int,
    val maxX: Int,
    val maxZ: Int,
    val surfaceY: Double
) {
    val id: Int = ID_GEN.getAndIncrement()
    val portals: MutableList<WalkPortal> = mutableListOf()
    var wallClearance: Int = 0

    fun centerVec(): Vec3 = Vec3(
        (minX + maxX + 1) * 0.5,
        surfaceY,
        (minZ + maxZ + 1) * 0.5
    )

    fun contains(blockX: Int, blockZ: Int): Boolean =
        blockX in minX..maxX && blockZ in minZ..maxZ

    fun nearestPointTo(target: Vec3): Vec3 {
        val cx = target.x.coerceIn(minX.toDouble(), (maxX + 1).toDouble())
        val cz = target.z.coerceIn(minZ.toDouble(), (maxZ + 1).toDouble())
        return Vec3(cx, surfaceY, cz)
    }

    override fun toString(): String =
        "WalkPolygon($id $minX..$maxX/$minZ..$maxZ y=$surfaceY clearance=$wallClearance portals=${portals.size})"

    companion object {
        private val ID_GEN = AtomicInteger(0)

        fun resetIds() = ID_GEN.set(0)

        fun feetVec(pos: BlockPos, y: Double): Vec3 = Vec3(pos.x + 0.5, y, pos.z + 0.5)
    }
}
