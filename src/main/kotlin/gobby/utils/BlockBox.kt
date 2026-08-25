package gobby.utils

import net.minecraft.world.phys.AABB
import kotlin.math.floor

data class BlockBox(
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int
) {
    val width: Int = maxX - minX + 1
    val height: Int = maxY - minY + 1
    val depth: Int = maxZ - minZ + 1
    val cellCount: Int = width * height * depth

    fun xAt(index: Int): Int = minX + index % width

    fun yAt(index: Int): Int = minY + index / (width * depth) % height

    fun zAt(index: Int): Int = minZ + index / width % depth

    companion object {
        fun covering(box: AABB): BlockBox = BlockBox(
            floor(box.minX).toInt(),
            floor(box.minY).toInt(),
            floor(box.minZ).toInt(),
            floor(box.maxX).toInt(),
            floor(box.maxY).toInt(),
            floor(box.maxZ).toInt()
        )
    }
}
