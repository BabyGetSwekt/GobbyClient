package gobby.features.mining.structurescanner

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.state.property.Property
import net.minecraft.util.math.BlockPos
import java.awt.Color

data class ColumnEntry<T : Comparable<T>>(
    val block: Block?,
    val property: Property<T>? = null,
    val value: T? = null
) {
    fun matches(state: BlockState): Boolean {
        if (block != null && state.block !== block) return false
        if (property != null && value != null && state.get(property) != value) return false
        return true
    }
}

data class Structure(
    val id: String,
    val displayName: String,
    val color: Color,
    val column: List<ColumnEntry<*>>,
    val waypointOffset: BlockPos = BlockPos.ORIGIN,
    val yRange: IntRange = 0..255,
    val island: String? = null,
    val unique: Boolean = true,
    val dedupRadius: Int = 0
) {
    val height get() = column.size
}
