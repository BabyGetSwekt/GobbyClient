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
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos

object FuckDiorite : Module(
    "Fuck Diorite",
    "Replaces the pillars in P2 with stained glass",
    Category.FLOOR7
) {

    private data class Pillar(val pos: BlockPos, val glass: Block) {
        val area: Iterable<BlockPos> = BlockPos.iterate(
            pos.add(-RADIUS, 0, -RADIUS),
            pos.add(RADIUS, HEIGHT, RADIUS)
        )

        fun replaceDiorite(world: ClientWorld) {
            for (pos in area) {
                if (world.getBlockAtPos(pos) in DIORITE_BLOCKS) {
                    world.setBlockAtPos(pos.toImmutable(), glass)
                }
            }
        }

        companion object {
            private const val RADIUS = 3
            private const val HEIGHT = 37
        }
    }

    private val pillars = arrayOf(
        Pillar(BlockPos(46, 169, 41), Blocks.LIME_STAINED_GLASS),
        Pillar(BlockPos(46, 169, 65), Blocks.YELLOW_STAINED_GLASS),
        Pillar(BlockPos(100, 169, 65), Blocks.PURPLE_STAINED_GLASS),
        Pillar(BlockPos(100, 169, 41), Blocks.RED_STAINED_GLASS)
    )

    private val DIORITE_BLOCKS = setOf(Blocks.DIORITE, Blocks.POLISHED_DIORITE)

    private val isActive: Boolean
        get() = dungeonFloor == 7 && inBoss && DungeonUtils.getPhase() in 2..3

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!enabled || !isActive) return
        val world = mc.world ?: return
        pillars.forEach { it.replaceDiorite(world) }
    }
}
