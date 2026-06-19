package gobby.features.floor7

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.utils.LocationUtils.inBoss
import gobby.utils.LocationUtils.dungeonFloor
import gobby.utils.skyblock.dungeon.DungeonUtils
import gobby.utils.Utils.getBlockAtPos
import gobby.utils.Utils.setBlockAtPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos

object FuckDiorite : Module(
    "Fuck Diorite",
    "Replaces the pillars in P2 with stained glass",
    Category.FLOOR7
) {

    private data class Pillar(val pos: BlockPos, val glass: Block) {
        val area: Iterable<BlockPos> = BlockPos.betweenClosed(
            pos.offset(-RADIUS, 0, -RADIUS),
            pos.offset(RADIUS, HEIGHT, RADIUS)
        )

        fun replaceDiorite(world: ClientLevel) {
            for (pos in area) {
                if (world.getBlockAtPos(pos) in DIORITE_BLOCKS) {
                    world.setBlockAtPos(pos.immutable(), glass)
                }
            }
        }

        companion object {
            private const val RADIUS = 3
            private const val HEIGHT = 37
        }
    }

    private val pillars = arrayOf(
        Pillar(BlockPos(46, 169, 41), Blocks.STAINED_GLASS.lime()),
        Pillar(BlockPos(46, 169, 65), Blocks.STAINED_GLASS.yellow()),
        Pillar(BlockPos(100, 169, 65), Blocks.STAINED_GLASS.purple()),
        Pillar(BlockPos(100, 169, 41), Blocks.STAINED_GLASS.red())
    )

    private val DIORITE_BLOCKS = setOf(Blocks.DIORITE, Blocks.POLISHED_DIORITE)

    private val isActive: Boolean
        get() = dungeonFloor == 7 && inBoss && DungeonUtils.getPhase() in 2..3

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!enabled || !isActive) return
        val world = mc.level ?: return
        pillars.forEach { it.replaceDiorite(world) }
    }
}
