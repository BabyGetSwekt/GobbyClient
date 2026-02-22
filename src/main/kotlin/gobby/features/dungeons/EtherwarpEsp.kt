package gobby.features.dungeons

import gobby.config.GobbyConfig
import gobby.events.core.SubscribeEvent
import gobby.events.dungeon.RoomEnterEvent
import gobby.features.render.BlockHighlighter
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.inDungeons
import gobby.utils.skyblock.dungeon.DungeonUtils.getRealCoords
import net.minecraft.block.BlockState
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import java.awt.Color

object EtherwarpEsp : BlockHighlighter() {

    private val highlightColor = Color(255, 255, 0, 60)

    override fun isEnabled(): Boolean = GobbyConfig.etherwarpEsp && inDungeons && !inBoss

    override fun getStatePredicate(): (BlockState) -> Boolean = { false }

    override fun getColor(pos: BlockPos): Color = highlightColor

    @SubscribeEvent
    fun onRoomEnter(event: RoomEnterEvent) {
        highlightedBlocks.clear()
        if (!isEnabled()) return
        val room = event.room ?: return
        val blocks = Brush.getRoomBlocks(room.data.name) ?: return

        for ((blockId, coords) in blocks) {
            val block = Registries.BLOCK.get(Identifier.of(blockId))
            if (block !in EtherwarpTriggerbot.TARGET_BLOCKS) continue
            for (encoded in coords) {
                val parts = encoded.substringBefore("|").split(",").map { it.trim().toInt() }
                val realPos = room.getRealCoords(BlockPos(parts[0], parts[1], parts[2]))
                highlightedBlocks.add(realPos)
            }
        }
    }
}
