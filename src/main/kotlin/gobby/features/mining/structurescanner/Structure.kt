package gobby.features.mining.structurescanner

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.core.BlockPos
import java.awt.Color

data class ColumnEntry<T : Comparable<T>>(
    val block: Block?,
    val property: Property<T>? = null,
    val value: T? = null
) {
    fun matches(state: BlockState): Boolean {
        if (block != null && state.block !== block) return false
        if (property != null && value != null && state.getValue(property) != value) return false
        return true
    }
}

data class Structure(
    val id: String,
    val displayName: String,
    val color: Color,
    val column: List<ColumnEntry<*>>,
    val waypointOffset: BlockPos = BlockPos.ZERO,
    val yRange: IntRange = 0..255,
    val island: String? = null,
    val unique: Boolean = true,
    val dedupRadius: Int = 0
) {
    val height get() = column.size
}
