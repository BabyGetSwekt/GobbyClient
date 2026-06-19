package gobby.features.mining.structurescanner

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.core.BlockPos
import java.awt.Color

object Structures {

    private val CRYSTAL_HOLLOWS_Y = 30..189

    val ALL: List<Structure> = listOf(
        Structure(
            id = "queen",
            displayName = "§6Queen",
            color = Color(255, 170, 0),
            column = listOf(
                col(Blocks.STONE),
                col(Blocks.ACACIA_LOG),
                col(Blocks.ACACIA_LOG),
                col(Blocks.ACACIA_LOG),
                col(Blocks.ACACIA_LOG),
                col(Blocks.CAULDRON)
            ),
            waypointOffset = BlockPos(0, 5, 0),
            yRange = CRYSTAL_HOLLOWS_Y,
        ),
        Structure(
            id = "king",
            displayName = "§6King",
            color = Color(255, 170, 0),
            column = listOf(
                col(Blocks.WOOL.white()),
                col(Blocks.DARK_OAK_STAIRS),
                col(Blocks.DARK_OAK_STAIRS),
                col(Blocks.DARK_OAK_STAIRS)
            ),
            waypointOffset = BlockPos(1, -1, 2),
            yRange = CRYSTAL_HOLLOWS_Y,
        ),
        Structure(
            id = "divan",
            displayName = "§2Divan",
            color = Color(0, 170, 0),
            column = listOf(
                col(Blocks.QUARTZ_BLOCK),
                col(Blocks.QUARTZ_STAIRS),
                col(Blocks.STONE_BRICK_STAIRS),
                col(Blocks.STONE_BRICKS)
            ),
            waypointOffset = BlockPos(0, 5, 0),
            yRange = CRYSTAL_HOLLOWS_Y,
        ),
        Structure(
            id = "city",
            displayName = "§bCity",
            color = Color(85, 255, 255),
            column = listOf(
                col(Blocks.COBBLESTONE),
                col(Blocks.COBBLESTONE),
                col(Blocks.COBBLESTONE),
                col(Blocks.COBBLESTONE),
                col(Blocks.COBBLESTONE_STAIRS),
                col(Blocks.POLISHED_ANDESITE),
                col(Blocks.POLISHED_ANDESITE),
                col(Blocks.DARK_OAK_STAIRS)
            ),
            waypointOffset = BlockPos(24, 0, -17),
            yRange = CRYSTAL_HOLLOWS_Y,
        ),
        Structure(
            id = "temple",
            displayName = "§5Temple",
            color = Color(170, 0, 170),
            column = listOf(
                col(Blocks.BEDROCK),
                col(Blocks.CLAY),
                col(Blocks.CLAY),
                col(Blocks.DYED_TERRACOTTA.white()),
                col(Blocks.WOOL.white()),
                col(Blocks.OAK_LEAVES),
                col(Blocks.OAK_LEAVES)
            ),
            waypointOffset = BlockPos(-45, 47, -18),
            yRange = CRYSTAL_HOLLOWS_Y,
        ),
        Structure(
            id = "fairy_grotto",
            displayName = "§dFairy Grotto",
            color = Color(255, 85, 255),
            column = listOf(col(Blocks.STAINED_GLASS.magenta())),
            yRange = CRYSTAL_HOLLOWS_Y,
            unique = false,
            dedupRadius = 5
        )
    )

    private fun col(block: Block) = ColumnEntry<Comparable<Comparable<*>>>(block)
}
