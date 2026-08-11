package gobby.pathfinder.world

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sign

class VoxelRay {
    var x = 0
    var y = 0
    var z = 0
    private var endX = 0
    private var endY = 0
    private var endZ = 0
    private var stepX = 0
    private var stepY = 0
    private var stepZ = 0
    private var tDeltaX = 0.0
    private var tDeltaY = 0.0
    private var tDeltaZ = 0.0
    private var tMaxX = 0.0
    private var tMaxY = 0.0
    private var tMaxZ = 0.0

    fun reset(startX: Double, startY: Double, startZ: Double, rayX: Double, rayY: Double, rayZ: Double): VoxelRay {
        x = floor(startX).toInt()
        y = floor(startY).toInt()
        z = floor(startZ).toInt()
        endX = floor(startX + rayX).toInt()
        endY = floor(startY + rayY).toInt()
        endZ = floor(startZ + rayZ).toInt()
        stepX = sign(rayX).toInt()
        stepY = sign(rayY).toInt()
        stepZ = sign(rayZ).toInt()
        tDeltaX = abs(inv(rayX) * stepX)
        tDeltaY = abs(inv(rayY) * stepY)
        tDeltaZ = abs(inv(rayZ) * stepZ)
        tMaxX = abs((x + max(stepX, 0) - startX) * inv(rayX))
        tMaxY = abs((y + max(stepY, 0) - startY) * inv(rayY))
        tMaxZ = abs((z + max(stepZ, 0) - startZ) * inv(rayZ))
        return this
    }

    val atEnd: Boolean get() = x == endX && y == endY && z == endZ

    fun advance() = when {
        tMaxX <= tMaxY && tMaxX <= tMaxZ -> { tMaxX += tDeltaX; x += stepX }
        tMaxY <= tMaxZ -> { tMaxY += tDeltaY; y += stepY }
        else -> { tMaxZ += tDeltaZ; z += stepZ }
    }

    private fun inv(v: Double) = if (v != 0.0) 1.0 / v else Double.MAX_VALUE

    companion object {
        private val LOCAL = ThreadLocal.withInitial { VoxelRay() }

        fun threadLocal(startX: Double, startY: Double, startZ: Double, rayX: Double, rayY: Double, rayZ: Double): VoxelRay =
            LOCAL.get().reset(startX, startY, startZ, rayX, rayY, rayZ)

        fun threadLocal(start: Vec3, ray: Vec3): VoxelRay = threadLocal(start.x, start.y, start.z, ray.x, ray.y, ray.z)
    }
}
