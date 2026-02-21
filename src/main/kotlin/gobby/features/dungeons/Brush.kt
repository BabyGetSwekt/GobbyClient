package gobby.features.dungeons

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import gobby.Gobbyclient.Companion.mc
import gobby.events.BlockStateChangeEvent
import gobby.events.ClientTickEvent
import gobby.events.LeftClickEvent
import gobby.events.RightClickEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.gui.brush.BlockSelector
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.DungeonUtils.getRelativeCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.SlabBlock
import net.minecraft.block.StairsBlock
import net.minecraft.block.WallBlock
import net.minecraft.block.enums.BlockHalf
import net.minecraft.block.enums.SlabType
import net.minecraft.client.world.ClientWorld
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.io.File

object Brush {

    var enabled = false
    private var rightClickUsed = false
    private var leftClickUsed = false
    private var wasInBoss = false

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dataType = object : TypeToken<MutableMap<String, MutableMap<String, MutableList<String>>>>() {}.type

    private val favoritesFile = File("./config/gobbyclientFabric/favorites.json")
    private val favoritesType = object : TypeToken<MutableSet<String>>() {}.type
    var favoriteBlocks: MutableSet<String> = mutableSetOf()
        private set

    private val configFile = File("./config/gobbyclientFabric/brush.json")
    private var brushData: MutableMap<String, MutableMap<String, MutableList<String>>> = mutableMapOf()

    private val bossConfigFile = File("./config/gobbyclientFabric/bossConfig.json")
    private var bossData: MutableMap<String, MutableMap<String, MutableList<String>>> = mutableMapOf()

    private val originalStates: MutableMap<BlockPos, BlockState> = mutableMapOf()

    private data class BrushContext(
        val coord: String,
        val blocks: MutableMap<String, MutableList<String>>,
        val save: () -> Unit
    )

    init {
        loadFromFile(configFile, "brush") { brushData = it }
        loadFromFile(bossConfigFile, "boss brush") { bossData = it }
        loadFavorites()
    }

    private fun coordStr(pos: BlockPos): String = "${pos.x}, ${pos.y}, ${pos.z}"

    private fun parseCoord(str: String): BlockPos {
        val parts = str.split(",").map { it.trim().toInt() }
        return BlockPos(parts[0], parts[1], parts[2])
    }

    private fun getTargetedBlock(): BlockHitResult? {
        val hit = mc.crosshairTarget
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return null
        return hit
    }

    private fun removeCoord(blocks: MutableMap<String, MutableList<String>>, coord: String): Boolean {
        val removed = blocks.values.any { it.remove(coord) }
        blocks.entries.removeAll { it.value.isEmpty() }
        return removed
    }

    private fun saveOriginalState(pos: BlockPos, world: ClientWorld) {
        originalStates.putIfAbsent(pos, world.getBlockState(pos))
    }

    private fun computePlacementState(defaultState: BlockState, hitResult: BlockHitResult): BlockState {
        val player = mc.player ?: return defaultState
        val block = defaultState.block
        val side = hitResult.side
        val hitY = hitResult.pos.y - hitResult.blockPos.y.toDouble()
        val isUpper = if (side == Direction.UP) false
            else if (side == Direction.DOWN) true
            else hitY > 0.5

        if (block is StairsBlock) {
            val facing = player.horizontalFacing
            val half = if (isUpper) BlockHalf.TOP else BlockHalf.BOTTOM
            return defaultState
                .with(StairsBlock.FACING, facing)
                .with(StairsBlock.HALF, half)
        }

        if (block is SlabBlock) {
            val slabType = if (isUpper) SlabType.TOP else SlabType.BOTTOM
            return defaultState.with(SlabBlock.TYPE, slabType)
        }

        return defaultState
    }

    /**
     * Resolves the brush context for the current position.
     * In boss mode, coordinates are absolute and data is keyed by floor.
     * In room mode, coordinates are relative to the room and data is keyed by room name.
     * Returns null if context cannot be resolved
     */
    private fun resolveContext(pos: BlockPos): BrushContext? {
        if (inBoss) {
            val blocks = bossData.getOrPut(dungeonFloor.toString()) { mutableMapOf() }
            return BrushContext(coordStr(pos), blocks, ::saveBoss)
        }
        val room = ScanUtils.currentRoom ?: return null
        val blocks = brushData.getOrPut(room.data.name) { mutableMapOf() }
        return BrushContext(coordStr(room.getRelativeCoords(pos)), blocks, ::save)
    }

    private fun resolveReadOnlyContext(pos: BlockPos): BrushContext? {
        if (inBoss) {
            val blocks = bossData[dungeonFloor.toString()] ?: return null
            return BrushContext(coordStr(pos), blocks, ::saveBoss)
        }
        val room = ScanUtils.currentRoom ?: return null
        val blocks = brushData[room.data.name] ?: return null
        return BrushContext(coordStr(room.getRelativeCoords(pos)), blocks, ::save)
    }

    private fun loadFromFile(
        file: File,
        label: String,
        assign: (MutableMap<String, MutableMap<String, MutableList<String>>>) -> Unit
    ) {
        if (!file.exists()) return
        try {
            assign(gson.fromJson(file.readText(), dataType) ?: mutableMapOf())
        } catch (e: Exception) {
            println("[GobbyClient] Failed to load $label data: ${e.message}")
            assign(mutableMapOf())
        }
    }

    private fun saveToFile(
        file: File,
        label: String,
        data: MutableMap<String, MutableMap<String, MutableList<String>>>
    ) {
        try {
            file.parentFile.mkdirs()
            file.writeText(gson.toJson(data))
        } catch (e: Exception) {
            println("[GobbyClient] Failed to save $label data: ${e.message}")
        }
    }

    fun toggleFavorite(blockId: String): Boolean {
        val added = if (blockId in favoriteBlocks) {
            favoriteBlocks.remove(blockId)
            false
        } else {
            favoriteBlocks.add(blockId)
            true
        }
        saveFavorites()
        return added
    }

    fun isFavorite(blockId: String): Boolean = blockId in favoriteBlocks

    private fun loadFavorites() {
        if (!favoritesFile.exists()) return
        try {
            favoriteBlocks = gson.fromJson(favoritesFile.readText(), favoritesType) ?: mutableSetOf()
        } catch (e: Exception) {
            println("[GobbyClient] Failed to load favorites: ${e.message}")
            favoriteBlocks = mutableSetOf()
        }
    }

    private fun saveFavorites() {
        try {
            favoritesFile.parentFile.mkdirs()
            favoritesFile.writeText(gson.toJson(favoriteBlocks))
        } catch (e: Exception) {
            println("[GobbyClient] Failed to save favorites: ${e.message}")
        }
    }

    private fun save() = saveToFile(configFile, "brush", brushData)

    private fun saveBoss() = saveToFile(bossConfigFile, "boss brush", bossData)

    private fun applyBlockData(
        world: ClientWorld,
        blockMap: Map<String, List<String>>,
        posMapper: (BlockPos) -> BlockPos = { it }
    ) {
        for ((blockId, coords) in blockMap) {
            val state = Registries.BLOCK.get(Identifier.of(blockId)).defaultState
            for (coord in coords) {
                val pos = posMapper(parseCoord(coord))
                val oldState = world.getBlockState(pos)
                originalStates.putIfAbsent(pos, oldState)
                world.setBlockState(pos, state)
                mc.worldRenderer.updateBlock(world, pos, oldState, state, 3)
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!mc.options.useKey.isPressed) rightClickUsed = false
        if (!mc.options.attackKey.isPressed) leftClickUsed = false

        if (inDungeons && inBoss && !wasInBoss) {
            val world = mc.world
            val floorBlocks = bossData[dungeonFloor.toString()]
            if (world != null && floorBlocks != null) applyBlockData(world, floorBlocks)
        }
        wasInBoss = inDungeons && inBoss

        if (!enabled) return
        if (!mc.options.pickItemKey.wasPressed()) return
        val world = mc.world ?: return
        val hitResult = getTargetedBlock() ?: return
        val block = world.getBlockState(hitResult.blockPos).block
        if (block == Blocks.AIR) return
        BlockSelector.selectedBlock = block
        modMessage("Selected block: §a${Registries.BLOCK.getId(block)}")
    }

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        if (!enabled) return
        if (!inDungeons) return
        if (rightClickUsed) { event.cancel(); return }
        val world = mc.world ?: return
        val selectedBlock = BlockSelector.selectedBlock ?: return
        val hitResult = getTargetedBlock() ?: return

        val placePos = hitResult.blockPos.offset(hitResult.side)
        val blockId = Registries.BLOCK.getId(selectedBlock).toString()
        val ctx = resolveContext(placePos) ?: return

        removeCoord(ctx.blocks, ctx.coord)
        ctx.blocks.getOrPut(blockId) { mutableListOf() }.add(ctx.coord)
        saveOriginalState(placePos, world)
        val state = computePlacementState(selectedBlock.defaultState, hitResult)
        world.setBlockState(placePos, state)
        ctx.save()

        rightClickUsed = true
        event.cancel()
    }

    @SubscribeEvent
    fun onLeftClick(event: LeftClickEvent) {
        if (!enabled) return
        if (!inDungeons) return
        if (leftClickUsed) { event.cancel(); return }
        val world = mc.world ?: return
        val hitResult = getTargetedBlock() ?: return
        val pos = hitResult.blockPos
        val ctx = resolveContext(pos) ?: return

        val found = removeCoord(ctx.blocks, ctx.coord)
        if (found) {
            val original = originalStates.remove(pos) ?: Blocks.AIR.defaultState
            world.setBlockState(pos, original)
        } else {
            val currentState = world.getBlockState(pos)
            if (currentState.isAir) return
            ctx.blocks.getOrPut("minecraft:air") { mutableListOf() }.add(ctx.coord)
            saveOriginalState(pos, world)
            world.setBlockState(pos, Blocks.AIR.defaultState)
            mc.worldRenderer.updateBlock(world, pos, currentState, Blocks.AIR.defaultState, 3)
        }
        ctx.save()

        leftClickUsed = true
        event.cancel()
    }

    @SubscribeEvent
    fun onBlockChange(event: BlockStateChangeEvent) {
        if (enabled) return
        if (!inDungeons) return

        val ctx = resolveReadOnlyContext(event.blockPos) ?: return
        if (ctx.blocks.values.any { ctx.coord in it }) event.cancel()
    }

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        val room = event.room ?: return
        val world = mc.world ?: return
        val roomBlocks = brushData[room.data.name] ?: return
        applyBlockData(world, roomBlocks) { room.getRealCoords(it) }
    }
}
