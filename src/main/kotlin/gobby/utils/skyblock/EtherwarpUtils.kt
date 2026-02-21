package gobby.utils.skyblock

import gobby.Gobbyclient.Companion.mc
import net.minecraft.block.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sign
import kotlin.reflect.KClass

/**
 * This code was inspired by Bloom's Etherwarp code, unlicensed
 * Source: Bloomcore, utils/Utils.js
 * @author UnclaimedBloom6
 */
object EtherwarpUtils {

    data class EtherPos(val succeeded: Boolean, val pos: BlockPos?) {
        companion object {
            val NONE = EtherPos(false, null)
        }
    }

    private val PASSABLE_TYPES: Set<KClass<*>> = setOf(
        AirBlock::class,
        FluidBlock::class,
        BubbleColumnBlock::class,
        NetherPortalBlock::class,
        FireBlock::class,
        PlantBlock::class,
        TallPlantBlock::class,
        ShortPlantBlock::class,
        TallFlowerBlock::class,
        StemBlock::class,
        CropBlock::class,
        SugarCaneBlock::class,
        SeagrassBlock::class,
        TallSeagrassBlock::class,
        VineBlock::class,
        AbstractPlantPartBlock::class,
        NetherWartBlock::class,
        SmallDripleafBlock::class,
        CarpetBlock::class,
        SnowBlock::class,
        RailBlock::class,
        ButtonBlock::class,
        LeverBlock::class,
        TorchBlock::class,
        LadderBlock::class,
        FlowerPotBlock::class,
        SkullBlock::class,
        WallSkullBlock::class,
        RedstoneWireBlock::class,
        ComparatorBlock::class,
        RepeaterBlock::class,
        RedstoneTorchBlock::class,
        TripwireBlock::class,
        TripwireHookBlock::class,
        CobwebBlock::class,
        PistonHeadBlock::class
    )

    fun getEtherPos(distance: Double = 57.0): EtherPos {
        val player = mc.player ?: return EtherPos.NONE
        val world = mc.world ?: return EtherPos.NONE

        val eyeHeight = if (player.isSneaking) 1.54 else 1.62
        val eyePos = Vec3d(player.x, player.y + eyeHeight, player.z)
        val endPos = eyePos.add(player.rotationVector.multiply(distance))

        return traverseVoxels(world, eyePos, endPos)
    }

    /**
     * DDA voxel traversal from start to end. Finds the first solid block
     * and checks that the 2 blocks above it are passable (valid landing spot).
     */
    private fun traverseVoxels(world: World, start: Vec3d, end: Vec3d): EtherPos {
        var x = floor(start.x).toInt()
        var y = floor(start.y).toInt()
        var z = floor(start.z).toInt()
        val endX = floor(end.x).toInt()
        val endY = floor(end.y).toInt()
        val endZ = floor(end.z).toInt()

        val dirX = end.x - start.x
        val dirY = end.y - start.y
        val dirZ = end.z - start.z

        val stepX = sign(dirX).toInt()
        val stepY = sign(dirY).toInt()
        val stepZ = sign(dirZ).toInt()

        val invX = safeInverse(dirX)
        val invY = safeInverse(dirY)
        val invZ = safeInverse(dirZ)

        val tDeltaX = abs(invX * stepX)
        val tDeltaY = abs(invY * stepY)
        val tDeltaZ = abs(invZ * stepZ)

        var tMaxX = abs((x + max(stepX, 0) - start.x) * invX)
        var tMaxY = abs((y + max(stepY, 0) - start.y) * invY)
        var tMaxZ = abs((z + max(stepZ, 0) - start.z) * invZ)

        repeat(1000) {
            val pos = BlockPos(x, y, z)

            if (!isPassable(world, pos)) {
                val canStand = isPassable(world, pos.up()) && isPassable(world, pos.up(2))
                return EtherPos(canStand, pos)
            }

            if (x == endX && y == endY && z == endZ) return EtherPos.NONE

            when {
                tMaxX <= tMaxY && tMaxX <= tMaxZ -> { tMaxX += tDeltaX; x += stepX }
                tMaxY <= tMaxZ -> { tMaxY += tDeltaY; y += stepY }
                else -> { tMaxZ += tDeltaZ; z += stepZ }
            }
        }

        return EtherPos.NONE
    }

    private fun isPassable(world: World, pos: BlockPos): Boolean {
        val block = world.getBlockState(pos).block
        return PASSABLE_TYPES.any { it.isInstance(block) }
    }

    private fun safeInverse(value: Double): Double =
        if (value != 0.0) 1.0 / value else Double.MAX_VALUE
}
