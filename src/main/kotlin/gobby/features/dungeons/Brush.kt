package gobby.features.dungeons

import gobby.Gobbyclient.Companion.mc
import gobby.events.BlockStateChangeEvent
import gobby.events.ClientTickEvent
import gobby.events.LeftClickEvent
import gobby.events.RightClickEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.gui.brush.BlockSelector
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import gobby.utils.skyblock.dungeon.DungeonUtils.getRelativeCoords
import gobby.utils.skyblock.dungeon.ScanUtils
import net.minecraft.world.level.block.Blocks
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import gobby.utils.ConfigUtils

object Brush : Module("Brush", "Applies your saved blocks to dungeon rooms and lets you place or remove them", Category.DUNGEONS) {

    private var wasEnabled = false
    private var rightClickUsed = false
    private var leftClickUsed = false
    private var wasInBoss = false
    private var wasInGui = false

    private val favoritesConfig = ConfigUtils.makeConfig("favorites") { FavoriteBlocks() }
    private val brushConfig = ConfigUtils.makeConfig("brush") { blockDataMap() }
    private val bossConfig = ConfigUtils.makeConfig("bossConfig") { blockDataMap() }

    val favoriteBlocks: MutableSet<String> get() = favoritesConfig.data.favorites
    var showFavoritesOnOpen: Boolean
        get() = favoritesConfig.data.showFavorites
        set(value) = favoritesConfig.edit { showFavorites = value }

    private val brushData get() = brushConfig.data
    private val bossData get() = bossConfig.data

    private val originalStates: MutableMap<BlockPos, BlockState> = mutableMapOf()

    private data class BrushContext(
        val coord: String,
        val blocks: MutableMap<String, MutableList<String>>,
        val save: () -> Unit
    )

    init {
    }

    private fun coordStr(pos: BlockPos): String = "${pos.x}, ${pos.y}, ${pos.z}"

    private fun getTargetedBlock(): BlockHitResult? {
        val hit = mc.hitResult
        if (hit !is BlockHitResult || hit.type != HitResult.Type.BLOCK) return null
        return hit
    }

    private fun removeCoord(blocks: MutableMap<String, MutableList<String>>, coord: String): Boolean {
        val removed = blocks.values.any { it.removeIf { entry -> BrushWorldOperations.coordinatePart(entry) == coord } }
        if (removed) blocks.values.removeIf { it.isEmpty() }
        return removed
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

    fun toggleFavorite(blockId: String): Boolean {
        val added = if (blockId in favoriteBlocks) { favoriteBlocks.remove(blockId)
        false }
                    else { favoriteBlocks.add(blockId); true }
        favoritesConfig.save()
        return added
    }

    fun isFavorite(blockId: String): Boolean = blockId in favoriteBlocks

    private fun save() = brushConfig.save()

    private fun saveBoss() = bossConfig.save()

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Pre) {
        if (enabled != wasEnabled) {
            wasEnabled = enabled
            if (enabled) applyCurrentRoom() else revertAll()
        }

        if (!mc.options.keyUse.isDown) rightClickUsed = false
        if (!mc.options.keyAttack.isDown) leftClickUsed = false

        val inGui = mc.gui.screen() != null
        if (wasInGui && !inGui) {
            rightClickUsed = mc.options.keyUse.isDown
            leftClickUsed = mc.options.keyAttack.isDown
        }
        wasInGui = inGui

        if (enabled && inDungeons && inBoss && !wasInBoss) {
            mc.level?.let { world -> bossData[dungeonFloor.toString()]?.let { BrushWorldOperations.applyBlockData(world, it, originalStates) } }
        }
        wasInBoss = inDungeons && inBoss

        if (!enabled) return
        if (!mc.options.keyPickItem.consumeClick()) return
        val world = mc.level ?: return
        val hitResult = getTargetedBlock() ?: return
        val block = world.getBlockState(hitResult.blockPos).block
        if (block == Blocks.AIR) return
        BlockSelector.selectedBlock = block
        modMessage("Selected block: §a${BuiltInRegistries.BLOCK.getKey(block)}")
    }

    @SubscribeEvent
    fun onRightClick(event: RightClickEvent) {
        if (!enabled) return
        if (!inDungeons) return
        if (mc.gui.screen() != null) return
        val hitResult = getTargetedBlock() ?: return
        event.cancel()
        if (rightClickUsed) return
        val world = mc.level ?: return
        val selectedBlock = BlockSelector.selectedBlock ?: return

        var placePos = hitResult.blockPos.relative(hitResult.direction)
        if (!world.getBlockState(placePos).isAir) placePos = placePos.relative(hitResult.direction)
        val blockId = BuiltInRegistries.BLOCK.getKey(selectedBlock).toString()
        val ctx = resolveContext(placePos) ?: return

        removeCoord(ctx.blocks, ctx.coord)
        BrushWorldOperations.rememberOriginalState(world, originalStates, placePos)
        val state = BrushWorldOperations.computePlacementState(selectedBlock.defaultBlockState(), hitResult)
        val encodedCoord = BrushWorldOperations.encodeCoordinate(ctx.coord, state)
        ctx.blocks.getOrPut(blockId) { mutableListOf() }.add(encodedCoord)
        world.setBlock(placePos, state, 3)
        ctx.save()

        rightClickUsed = true
    }

    @SubscribeEvent
    fun onLeftClick(event: LeftClickEvent) {
        if (!enabled) return
        if (!inDungeons) return
        if (mc.gui.screen() != null) return
        val hitResult = getTargetedBlock() ?: return
        event.cancel()
        if (leftClickUsed) return
        val world = mc.level ?: return
        val pos = hitResult.blockPos
        val ctx = resolveContext(pos) ?: return

        val found = removeCoord(ctx.blocks, ctx.coord)
        if (found) {
            val original = originalStates.remove(pos) ?: Blocks.AIR.defaultBlockState()
            world.setBlock(pos, original, 3)
        } else {
            val currentState = world.getBlockState(pos)
            if (currentState.isAir) return
            ctx.blocks.getOrPut("minecraft:air") { mutableListOf() }.add(ctx.coord)
            BrushWorldOperations.rememberOriginalState(world, originalStates, pos)
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)
            world.sendBlockUpdated(pos, currentState, Blocks.AIR.defaultBlockState(), 3)
        }
        ctx.save()

        leftClickUsed = true
    }

    @SubscribeEvent
    fun onBlockChange(event: BlockStateChangeEvent) {
        if (!enabled || !inDungeons) return

        val ctx = resolveContext(event.blockPos, writable = false) ?: return
        val isTracked = ctx.blocks.values.any { it.any { entry -> BrushWorldOperations.coordinatePart(entry) == ctx.coord } }
        if (isTracked) event.cancel()
    }

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        if (!enabled) return
        val room = event.room ?: return
        val world = mc.level ?: return
        val roomBlocks = brushData[room.data.name] ?: return
        BrushWorldOperations.applyBlockData(world, roomBlocks, originalStates) { room.getRealCoords(it) }
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        originalStates.clear()
        wasInBoss = false
    }

    private fun applyCurrentRoom() {
        val world = mc.level ?: return
        if (inBoss) {
            bossData[dungeonFloor.toString()]?.let { BrushWorldOperations.applyBlockData(world, it, originalStates) }
        } else {
            val room = ScanUtils.currentRoom ?: return
            brushData[room.data.name]?.let { BrushWorldOperations.applyBlockData(world, it, originalStates) { pos -> room.getRealCoords(pos) } }
        }
    }

    private fun revertAll() {
        val world = mc.level ?: return
        originalStates.forEach { (pos, state) ->
            val current = world.getBlockState(pos)
            world.setBlock(pos, state, 3)
            world.sendBlockUpdated(pos, current, state, 3)
        }
        originalStates.clear()
    }
}
