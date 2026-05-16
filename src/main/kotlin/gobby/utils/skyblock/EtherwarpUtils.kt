package gobby.utils.skyblock

import gobby.Gobbyclient.Companion.mc
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sign

object EtherwarpUtils {

    data class EtherPos(val succeeded: Boolean, val pos: BlockPos?, val state: BlockState? = null) {
        val vec3: Vec3 by lazy { Vec3(pos ?: BlockPos.ZERO) }

        companion object {
            val NONE = EtherPos(false, null)
        }
    }

    fun getEtherPos(distance: Double = 57.0): EtherPos =
        getEtherPos(mc.player?.position(), distance, etherWarp = true)

    fun getEtherPos(
        position: Vec3?,
        distance: Double,
        returnEnd: Boolean = false,
        etherWarp: Boolean = false
    ): EtherPos {
        val player = mc.player ?: return EtherPos.NONE
        if (position == null) return EtherPos.NONE

        val eyeHeight = if (player.isCrouching) 1.54 else 1.62
        val startPos = position.add(0.0, eyeHeight, 0.0)
        val endPos = player.lookAngle.multiply(distance, distance, distance).add(startPos)

        return traverseVoxels(startPos, endPos, etherWarp)
            .takeUnless { it == EtherPos.NONE && returnEnd }
            ?: EtherPos(true, BlockPos.containing(endPos), null)
    }

    private fun traverseVoxels(start: Vec3, end: Vec3, etherWarp: Boolean): EtherPos {
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
            val world = mc.level ?: return EtherPos.NONE
            val chunk = world.getChunk(SectionPos.blockToSectionCoord(pos.x), SectionPos.blockToSectionCoord(pos.z))
            val state = chunk.getBlockState(pos)
            val id = Block.getId(state)
            val flags = blockFlags[id]
            val isPassable = (flags and PASSABLE) != 0
            val isSolid = !isPassable

            if ((etherWarp && isSolid) || (!etherWarp && id != 0)) {
                if (!etherWarp && isPassable) return EtherPos(false, pos, state)
                val collisionTop = state.getCollisionShape(world, pos).max(Direction.Axis.Y)
                val clearanceBaseY = pos.y + max(1, ceil(collisionTop).toInt())
                val feetState = chunk.getBlockState(BlockPos(pos.x, clearanceBaseY, pos.z))
                val feetFlags = blockFlags[Block.getId(feetState)]
                if ((feetFlags and PASSABLE) == 0 || (feetFlags and BLOCKS_FEET) != 0) return EtherPos(false, pos, state)
                val headState = chunk.getBlockState(BlockPos(pos.x, clearanceBaseY + 1, pos.z))
                val headFlags = blockFlags[Block.getId(headState)]
                if ((headFlags and PASSABLE) == 0 || (headFlags and BLOCKS_FEET) != 0) return EtherPos(false, pos, state)
                return EtherPos(true, pos, state)
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

    private fun safeInverse(value: Double): Double =
        if (value != 0.0) 1.0 / value else Double.MAX_VALUE

    private const val PASSABLE = 1
    private const val BLOCKS_FEET = 2

    private val blockFlags: IntArray = IntArray(Block.BLOCK_STATE_REGISTRY.size()).apply {
        Block.BLOCK_STATE_REGISTRY.forEach { state ->
            val block = state.block
            val id = Block.getId(state)
            val passable = when (block) {
                is AirBlock -> true
                is FlowerBlock, is TallGrassBlock, is BushBlock, is TallFlowerBlock, is ShortDryGrassBlock -> true
                is TorchBlock, is RedstoneTorchBlock -> true
                is TripWireBlock, is TripWireHookBlock -> true
                is RailBlock -> true
                is FireBlock -> true
                is VineBlock -> true
                is LiquidBlock -> true
                is SaplingBlock -> true
                is CropBlock, is StemBlock -> true
                is SeagrassBlock, is TallSeagrassBlock -> true
                is SugarCaneBlock -> true
                is MushroomBlock -> true
                is NetherWartBlock -> true
                is RedStoneWireBlock, is ComparatorBlock, is RepeaterBlock -> true
                is SmallDripleafBlock, is BigDripleafStemBlock -> true
                is DoublePlantBlock -> true
                is LeverBlock -> true
                is SnowLayerBlock -> true
                is BubbleColumnBlock -> true
                is GrowingPlantBlock -> true
                is PistonHeadBlock -> true
                is DryVegetationBlock -> true
                is ButtonBlock -> true
                is LanternBlock -> true
                is SkullBlock, is WallSkullBlock -> true
                is LadderBlock -> true
                is FlowerPotBlock -> true
                is WebBlock -> true
                is NetherPortalBlock -> true
                else -> false
            }
            val blocksFeet = when (block) {
                is SkullBlock, is WallSkullBlock -> true
                is FlowerPotBlock -> true
                is LadderBlock -> true
                is VineBlock -> true
                else -> false
            }
            var flags = 0
            if (passable) flags = flags or PASSABLE
            if (blocksFeet) flags = flags or BLOCKS_FEET
            this[id] = flags
        }
    }
}
