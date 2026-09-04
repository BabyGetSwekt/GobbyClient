package gobby.utils.skyblock.dungeon.map

import gobby.Gobbyclient.Companion.mc
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.utils.timer.Clock
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.minecraft.world.level.block.state.properties.ChestType

object DungeonMimic {

    private const val SCAN_INTERVAL_MS = 1000L
    private const val BLOCK_CENTER = 0.5

    private val chestsByRoom = mutableMapOf<Int, MutableSet<BlockPos>>()
    private val mimicRooms = mutableSetOf<Int>()
    private val scanClock = Clock()

    fun isMimicRoom(grid: Array<MapTile>, cell: Int): Boolean =
        mimicRooms.isNotEmpty() && DungeonRooms.canonical(grid, cell) in mimicRooms

    fun update(grid: Array<MapTile>) {
        if (!scanClock.hasTimePassed(SCAN_INTERVAL_MS, setTime = true)) return
        loadedTrappedChests().forEach { pos -> register(grid, pos) }
    }

    private fun loadedTrappedChests(): List<BlockPos> {
        val level = mc.level ?: return emptyList()
        val source = level.chunkSource
        val (minChunkX, minChunkZ, maxChunkX, maxChunkZ) = MapGrid.dungeonChunkBounds()
        return (minChunkX..maxChunkX).flatMap { chunkX ->
            (minChunkZ..maxChunkZ).filter { source.hasChunk(chunkX, it) }.flatMap { chunkZ ->
                level.getChunk(chunkX, chunkZ).blockEntities.values.filter(::isSingleTrappedChest).map { it.blockPos }
            }
        }
    }

    private fun isSingleTrappedChest(entity: Any?): Boolean {
        if (entity !is ChestBlockEntity) return false
        val state = entity.blockState
        return state.`is`(Blocks.TRAPPED_CHEST) && state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
    }

    private fun register(grid: Array<MapTile>, pos: BlockPos) {
        val cell = DungeonRooms.containingRoomCell(grid, pos.x + BLOCK_CENTER, pos.z + BLOCK_CENTER) ?: return
        val expected = (grid.getOrNull(cell) as? MapTile.Room)?.data?.trappedChests ?: return
        val room = DungeonRooms.canonical(grid, cell)
        val found = chestsByRoom.getOrPut(room) { mutableSetOf() }
        if (found.add(pos.immutable()) && found.size > expected) mimicRooms += room
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldLoadEvent) {
        chestsByRoom.clear()
        mimicRooms.clear()
    }
}
