package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.phys.BlockHitResult

internal object BrushWorldOperations {
    fun coordinatePart(encoded: String): String = encoded.substringBefore("|")

    fun parseCoordinate(encoded: String): BlockPos {
        val values = coordinatePart(encoded).split(",").map { it.trim().toInt() }
        return BlockPos(values[0], values[1], values[2])
    }

    fun encodeCoordinate(coordinate: String, state: BlockState): String = when (state.block) {
        is StairBlock -> "$coordinate|facing=${state.getValue(StairBlock.FACING).serializedName},half=${state.getValue(StairBlock.HALF).serializedName}"
        is SlabBlock -> "$coordinate|type=${state.getValue(SlabBlock.TYPE).serializedName}"
        else -> coordinate
    }

    fun computePlacementState(defaultState: BlockState, hit: BlockHitResult): BlockState {
        val player = mc.player ?: return defaultState
        val upper = when (hit.direction) {
            Direction.UP -> false
            Direction.DOWN -> true
            else -> hit.location.y - hit.blockPos.y > 0.5
        }
        return when (defaultState.block) {
            is StairBlock -> defaultState.setValue(StairBlock.FACING, player.direction).setValue(StairBlock.HALF, if (upper) Half.TOP else Half.BOTTOM)
            is SlabBlock -> defaultState.setValue(SlabBlock.TYPE, if (upper) SlabType.TOP else SlabType.BOTTOM)
            else -> defaultState
        }
    }

    fun rememberOriginalState(world: ClientLevel, originalStates: MutableMap<BlockPos, BlockState>, position: BlockPos) {
        originalStates.putIfAbsent(position, world.getBlockState(position))
    }

    fun applyBlockData(
        world: ClientLevel,
        blockMap: Map<String, List<String>>,
        originalStates: MutableMap<BlockPos, BlockState>,
        positionMapper: (BlockPos) -> BlockPos = { it }
    ) {
        val stairPositions = blockMap.flatMap { (blockId, coordinates) ->
            coordinates.map { encoded -> applyBlock(world, originalStates, blockId, encoded, positionMapper) }
        }.filter { it.second }.map { it.first }
        val random = RandomSource.create()
        stairPositions.flatMap { position ->
            Direction.entries.map { direction -> position to direction }
        }.forEach { (position, direction) -> updateNeighbor(world, position, direction, random) }
    }

    private fun applyBlock(world: ClientLevel, originalStates: MutableMap<BlockPos, BlockState>, blockId: String, encoded: String, mapper: (BlockPos) -> BlockPos): Pair<BlockPos, Boolean> {
        val position = mapper(parseCoordinate(encoded))
        val state = decodeState(blockId, encoded)
        val previous = world.getBlockState(position)
        originalStates.putIfAbsent(position, previous)
        world.setBlock(position, state, UPDATE_FLAGS)
        world.sendBlockUpdated(position, previous, state, UPDATE_FLAGS)
        return position to (state.block is StairBlock)
    }

    private fun updateNeighbor(world: ClientLevel, position: BlockPos, direction: Direction, random: RandomSource) {
        val current = world.getBlockState(position)
        val updated = current.updateShape(world, world, position, direction, position.relative(direction), world.getBlockState(position.relative(direction)), random)
        if (updated != current) {
            world.setBlock(position, updated, UPDATE_FLAGS)
            world.sendBlockUpdated(position, current, updated, UPDATE_FLAGS)
        }
    }

    private fun decodeState(blockId: String, encoded: String): BlockState {
        val block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId))
        var state = block.defaultBlockState()
        val properties = encoded.substringAfter('|', "").split(",").filter(String::isNotEmpty)
        properties.forEach { property ->
            val (key, value) = property.split("=", limit = 2)
            state = when (key) {
                "facing" -> if (block is StairBlock) state.setValue(StairBlock.FACING, Direction.valueOf(value.uppercase())) else state
                "half" -> if (block is StairBlock) state.setValue(StairBlock.HALF, Half.valueOf(value.uppercase())) else state
                "type" -> if (block is SlabBlock) state.setValue(SlabBlock.TYPE, SlabType.valueOf(value.uppercase())) else state
                else -> state
            }
        }
        return state
    }

    private const val UPDATE_FLAGS = 3
}
