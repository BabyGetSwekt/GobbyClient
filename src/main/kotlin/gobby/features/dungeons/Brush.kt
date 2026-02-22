package gobby.features.dungeons

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
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
    private var wasInGui = false

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dataType = object : TypeToken<MutableMap<String, MutableMap<String, MutableList<String>>>>() {}.type

    private val favoritesFile = File("./config/gobbyclientFabric/favorites.json")
    private val favoritesSetType = object : TypeToken<MutableSet<String>>() {}.type
    var favoriteBlocks: MutableSet<String> = mutableSetOf()
        private set
    var showFavoritesOnOpen = false

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

    private fun coordPart(encoded: String): String = encoded.substringBefore("|")

    private fun parseCoord(str: String): BlockPos {
        val parts = coordPart(str).split(",").map { it.trim().toInt() }
        return BlockPos(parts[0], parts[1], parts[2])
    }

    private fun encodeCoord(coord: String, state: BlockState): String = when (state.block) {
        is StairsBlock -> "$coord|facing=${state.get(StairsBlock.FACING).asString()},half=${state.get(StairsBlock.HALF).asString()}"
        is SlabBlock -> "$coord|type=${state.get(SlabBlock.TYPE).asString()}"
        else -> coord
    }

    private fun decodeState(blockId: String, encoded: String): BlockState {
        val block = Registries.BLOCK.get(Identifier.of(blockId))
        var state = block.defaultState
        val pipeIdx = encoded.indexOf('|')
        if (pipeIdx == -1) return state

        for (prop in encoded.substring(pipeIdx + 1).split(",")) {
            val (key, value) = prop.split("=", limit = 2)
            when (key) {
                "facing" -> if (block is StairsBlock) state = state.with(StairsBlock.FACING, Direction.valueOf(value.uppercase()))
                "half" -> if (block is StairsBlock) state = state.with(StairsBlock.HALF, BlockHalf.valueOf(value.uppercase()))
                "type" -> if (block is SlabBlock) state = state.with(SlabBlock.TYPE, SlabType.valueOf(value.uppercase()))
            }
        }
        return state
    }

    private fun getTargetedBlock(): BlockHitResult? {
        val hit = mc.crosshairTarget
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return null
        return hit
    }

    private fun removeCoord(blocks: MutableMap<String, MutableList<String>>, coord: String): Boolean {
        val removed = blocks.values.any { it.removeIf { entry -> coordPart(entry) == coord } }
        if (removed) blocks.values.removeIf { it.isEmpty() }
        return removed
    }

    private fun saveOriginalState(pos: BlockPos, world: ClientWorld) {
        originalStates.putIfAbsent(pos, world.getBlockState(pos))
    }

    private fun computePlacementState(defaultState: BlockState, hitResult: BlockHitResult): BlockState {
        val player = mc.player ?: return defaultState
        val hitY = hitResult.pos.y - hitResult.blockPos.y.toDouble()
        val isUpper = when (hitResult.side) {
            Direction.UP -> false
            Direction.DOWN -> true
            else -> hitY > 0.5
        }

        return when (defaultState.block) {
            is StairsBlock -> defaultState
                .with(StairsBlock.FACING, player.horizontalFacing)
                .with(StairsBlock.HALF, if (isUpper) BlockHalf.TOP else BlockHalf.BOTTOM)
            is SlabBlock -> defaultState
                .with(SlabBlock.TYPE, if (isUpper) SlabType.TOP else SlabType.BOTTOM)
            else -> defaultState
        }
    }

    /**
     * Resolves the brush context for the current position.
     * In boss mode, coordinates are absolute and data is keyed by floor.
     * In room mode, coordinates are relative to the room and data is keyed by room name.
     * When [writable] is true, missing map entries are created; otherwise returns null.
     */
    private fun resolveContext(pos: BlockPos, writable: Boolean = true): BrushContext? {
        if (inBoss) {
            val key = dungeonFloor.toString()
            val blocks = if (writable) bossData.getOrPut(key) { mutableMapOf() } else bossData[key] ?: return null
            return BrushContext(coordStr(pos), blocks, ::saveBoss)
        }
        val room = ScanUtils.currentRoom ?: return null
        val key = room.data.name
        val blocks = if (writable) brushData.getOrPut(key) { mutableMapOf() } else brushData[key] ?: return null
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
        val added = if (blockId in favoriteBlocks) { favoriteBlocks.remove(blockId); false }
                    else { favoriteBlocks.add(blockId); true }
        saveFavorites()
        return added
    }

    fun isFavorite(blockId: String): Boolean = blockId in favoriteBlocks

    fun getRoomBlocks(roomName: String): Map<String, List<String>>? = brushData[roomName]

    private fun loadFavorites() {
        if (!favoritesFile.exists()) return
        try {
            val json = gson.fromJson(favoritesFile.readText(), JsonObject::class.java)
            favoriteBlocks = gson.fromJson(json.getAsJsonArray("blocks"), favoritesSetType) ?: mutableSetOf()
            showFavoritesOnOpen = json.get("showFavorites")?.asBoolean ?: false
        } catch (e: Exception) {
            println("[GobbyClient] Failed to load favorites: ${e.message}")
            favoriteBlocks = mutableSetOf()
        }
    }

    private fun saveFavorites() {
        try {
            favoritesFile.parentFile.mkdirs()
            val json = JsonObject()
            json.add("blocks", gson.toJsonTree(favoriteBlocks))
            json.addProperty("showFavorites", showFavoritesOnOpen)
            favoritesFile.writeText(gson.toJson(json))
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
        val stairPositions = mutableListOf<BlockPos>()
        for ((blockId, coords) in blockMap) {
            val block = Registries.BLOCK.get(Identifier.of(blockId))
            for (encoded in coords) {
                val pos = posMapper(parseCoord(encoded))
                val state = decodeState(blockId, encoded)
                val oldState = world.getBlockState(pos)
                saveOriginalState(pos, world)
                world.setBlockState(pos, state)
                mc.worldRenderer.updateBlock(world, pos, oldState, state, 3)
                if (block is StairsBlock) stairPositions.add(pos)
            }
        }

        val random = net.minecraft.util.math.random.Random.create()
        for (pos in stairPositions) {
            var state = world.getBlockState(pos)
            for (dir in Direction.entries) {
                val neighborPos = pos.offset(dir)
                state = state.getStateForNeighborUpdate(world, world, pos, dir, neighborPos, world.getBlockState(neighborPos), random)
            }
            val current = world.getBlockState(pos)
            if (state != current) {
                world.setBlockState(pos, state)
                mc.worldRenderer.updateBlock(world, pos, current, state, 3)
            }
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (!mc.options.useKey.isPressed) rightClickUsed = false
        if (!mc.options.attackKey.isPressed) leftClickUsed = false

        val inGui = mc.currentScreen != null
        if (wasInGui && !inGui) {
            rightClickUsed = mc.options.useKey.isPressed
            leftClickUsed = mc.options.attackKey.isPressed
        }
        wasInGui = inGui

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
        if (mc.currentScreen != null) return
        val hitResult = getTargetedBlock() ?: return
        event.cancel()
        if (rightClickUsed) return
        val world = mc.world ?: return
        val selectedBlock = BlockSelector.selectedBlock ?: return

        var placePos = hitResult.blockPos.offset(hitResult.side)
        if (!world.getBlockState(placePos).isAir) placePos = placePos.offset(hitResult.side)
        val blockId = Registries.BLOCK.getId(selectedBlock).toString()
        val ctx = resolveContext(placePos) ?: return

        removeCoord(ctx.blocks, ctx.coord)
        saveOriginalState(placePos, world)
        val state = computePlacementState(selectedBlock.defaultState, hitResult)
        val encodedCoord = encodeCoord(ctx.coord, state)
        ctx.blocks.getOrPut(blockId) { mutableListOf() }.add(encodedCoord)
        world.setBlockState(placePos, state)
        ctx.save()

        rightClickUsed = true
    }

    @SubscribeEvent
    fun onLeftClick(event: LeftClickEvent) {
        if (!enabled) return
        if (!inDungeons) return
        if (mc.currentScreen != null) return
        val hitResult = getTargetedBlock() ?: return
        event.cancel()
        if (leftClickUsed) return
        val world = mc.world ?: return
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
    }

    @SubscribeEvent
    fun onBlockChange(event: BlockStateChangeEvent) {
        if (enabled) return
        if (!inDungeons) return

        val ctx = resolveContext(event.blockPos, writable = false) ?: return
        val isTracked = ctx.blocks.values.any { it.any { entry -> coordPart(entry) == ctx.coord } }
        if (isTracked) event.cancel()
    }

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        val room = event.room ?: return
        val world = mc.world ?: return
        val roomBlocks = brushData[room.data.name] ?: return
        applyBlockData(world, roomBlocks) { room.getRealCoords(it) }
    }
}
