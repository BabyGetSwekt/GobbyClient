package gobby.utils.copy

import net.minecraft.block.BlockState
import net.minecraft.registry.Registries
import net.minecraft.state.property.Property
import net.minecraft.util.Identifier

object BlockStateCodec {

    fun encode(state: BlockState): String {
        val blockId = Registries.BLOCK.getId(state.block).toString()
        val props = state.entries.entries
        if (props.isEmpty()) return blockId
        val propStr = props.joinToString(",") { (k, v) ->
            @Suppress("UNCHECKED_CAST")
            val prop = k as Property<Comparable<Any>>
            @Suppress("UNCHECKED_CAST")
            "${prop.name}=${prop.name(v as Comparable<Any>)}"
        }
        return "$blockId[$propStr]"
    }

    fun decode(stateStr: String): BlockState? {
        val bracketIdx = stateStr.indexOf('[')
        val blockId = if (bracketIdx == -1) stateStr else stateStr.substring(0, bracketIdx)
        val block = Registries.BLOCK.get(Identifier.of(blockId))
        var state = block.defaultState
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
        val property = state.block.stateManager.getProperty(key) ?: return null
        @Suppress("UNCHECKED_CAST")
        val parsed = (property as Property<Comparable<Any>>).parse(value)
        return if (parsed.isPresent) state.with(property, parsed.get()) else null
    }
}
