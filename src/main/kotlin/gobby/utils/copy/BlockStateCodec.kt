package gobby.utils.copy

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.resources.Identifier as ResourceLocation

object BlockStateCodec {

    fun encode(state: BlockState): String {
        val blockId = BuiltInRegistries.BLOCK.getKey(state.block).toString()
        val props = state.values.toList()
        if (props.isEmpty()) return blockId
        val propStr = props.joinToString(",") { "${it.property().name}=${it.valueName()}" }
        return "$blockId[$propStr]"
    }

    fun decode(stateStr: String): BlockState? {
        val bracketIdx = stateStr.indexOf('[')
        val blockId = if (bracketIdx == -1) stateStr else stateStr.substring(0, bracketIdx)
        val block = BuiltInRegistries.BLOCK.getValue(ResourceLocation.parse(blockId))
        var state = block.defaultBlockState()
        if (bracketIdx == -1) return state
        val propsStr = stateStr.substring(bracketIdx + 1, stateStr.length - 1)
        for (prop in propsStr.split(",")) {
            val eqIdx = prop.indexOf('=')
            if (eqIdx == -1) continue
            state = applyProperty(state, prop.substring(0, eqIdx), prop.substring(eqIdx + 1)) ?: state
        }
        return state
    }

    private fun applyProperty(state: BlockState, key: String, value: String): BlockState? {
        val property = state.block.stateDefinition.getProperty(key) ?: return null

        @Suppress("UNCHECKED_CAST")
        val parsed = (property as Property<Comparable<Any>>).getValue(value)
        return if (parsed.isPresent) state.setValue(property, parsed.get()) else null
    }
}
