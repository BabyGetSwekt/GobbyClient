package gobby.utils.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.utils.PlayerUtils
import gobby.utils.getEtherTransmissionRange
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

    fun getEtherPos(): EtherPos {
        val range = mc.player?.mainHandItem?.getEtherTransmissionRange()?.toDouble() ?: 0.0
        return getEtherPos(mc.player?.position(), range, etherWarp = true)
    }

    fun getEtherPos(
        position: Vec3?,
        distance: Double,
        returnEnd: Boolean = false,
        etherWarp: Boolean = false
    ): EtherPos {
        val player = mc.player ?: return EtherPos.NONE
        if (position == null) return EtherPos.NONE

        val startPos = position.add(0.0, PlayerUtils.getEyeHeight(), 0.0)
        val endPos = player.lookAngle.multiply(distance, distance, distance).add(startPos)

        return traverseVoxels(startPos, endPos, etherWarp)
            .takeUnless { it == EtherPos.NONE && returnEnd }
            ?: EtherPos(true, BlockPos.containing(endPos), null)
    }

    /**
     * DDA voxel raycaster which I adapted from Odin.
     * Original DDA logic by UnclaimedBloom6, integrated into Odin by Odtheking under BSD 3-Clause license.
     * This function has been modified by me.
     * Source: https://github.com/odtheking/Odin/blob/77b66713f74849bbcc05067484e6e85c01c96698/src/main/kotlin/com/odtheking/odin/features/impl/render/Etherwarp.kt#L142
     */
    private fun traverseVoxels(start: Vec3, end: Vec3, etherWarp: Boolean): EtherPos {
        val world = mc.level ?: return EtherPos.NONE
        val minY = world.minY
        val maxY = world.maxY

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

        val cursor = BlockPos.MutableBlockPos()
        var cachedChunkX = SectionPos.blockToSectionCoord(x)
        var cachedChunkZ = SectionPos.blockToSectionCoord(z)
        var chunk = world.getChunk(cachedChunkX, cachedChunkZ)

        repeat(1000) {
            if (y !in minY..<maxY) return EtherPos.NONE

            val cx = SectionPos.blockToSectionCoord(x)
            val cz = SectionPos.blockToSectionCoord(z)
            if (cx != cachedChunkX || cz != cachedChunkZ) {
                chunk = world.getChunk(cx, cz)
                cachedChunkX = cx
                cachedChunkZ = cz
            }

            cursor.set(x, y, z)
            val state = chunk.getBlockState(cursor)
            val id = Block.getId(state)
            val flags = blockFlags[id]
            val isPassable = (flags and PASSABLE) != 0

            if ((etherWarp && !isPassable) || (!etherWarp && id != 0)) {
                val hit = cursor.immutable()
                if (!etherWarp && isPassable) return EtherPos(false, hit, state)
                if (flags hasFlag LANDING_BLACKLIST) return EtherPos(false, hit, state)
                val collisionTop = state.getCollisionShape(world, hit).max(Direction.Axis.Y)
                val clearanceBaseY = hit.y + max(1, ceil(collisionTop).toInt())
                val feetFlags = blockFlags[Block.getId(chunk.getBlockState(cursor.set(hit.x, clearanceBaseY, hit.z)))]
                if (!isValidFeetSpot(feetFlags)) return EtherPos(false, hit, state)
                val headFlags = blockFlags[Block.getId(chunk.getBlockState(cursor.set(hit.x, clearanceBaseY + 1, hit.z)))]
                if (!isValidHeadSpot(headFlags)) return EtherPos(false, hit, state)
                return EtherPos(true, hit, state)
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

    private infix fun Int.hasFlag(bit: Int): Boolean = (this and bit) != 0

    private fun isValidFeetSpot(flags: Int): Boolean =
        flags hasFlag FEET_PASSABLE && !(flags hasFlag BLOCKS_FEET) && !(flags hasFlag LANDING_BLACKLIST)

    private fun isValidHeadSpot(flags: Int): Boolean =
        flags hasFlag PASSABLE && !(flags hasFlag BLOCKS_FEET)

    private const val PASSABLE = 1
    private const val BLOCKS_FEET = 2
    private const val FEET_PASSABLE = 4
    private const val LANDING_BLACKLIST = 8

    private val passableWhitelist: Set<Class<out Block>> = setOf(
        AirBlock::class.java,
        FlowerBlock::class.java, TallGrassBlock::class.java, BushBlock::class.java,
        TallFlowerBlock::class.java, ShortDryGrassBlock::class.java,
        TorchBlock::class.java, RedstoneTorchBlock::class.java,
        TripWireBlock::class.java, TripWireHookBlock::class.java,
        RailBlock::class.java, FireBlock::class.java, VineBlock::class.java,
        LiquidBlock::class.java, SaplingBlock::class.java,
        CropBlock::class.java, StemBlock::class.java,
        SeagrassBlock::class.java, TallSeagrassBlock::class.java,
        SugarCaneBlock::class.java, MushroomBlock::class.java, NetherWartBlock::class.java,
        RedStoneWireBlock::class.java, ComparatorBlock::class.java, RepeaterBlock::class.java,
        SmallDripleafBlock::class.java, BigDripleafStemBlock::class.java,
        DoublePlantBlock::class.java, LeverBlock::class.java, LeafLitterBlock::class.java,
        BubbleColumnBlock::class.java, GrowingPlantBlock::class.java,
        PistonHeadBlock::class.java, DryVegetationBlock::class.java,
        ButtonBlock::class.java,
        LanternBlock::class.java, SkullBlock::class.java, WallSkullBlock::class.java,
        LadderBlock::class.java, FlowerPotBlock::class.java,
        WebBlock::class.java, NetherPortalBlock::class.java,
    )

    private val blocksFeetWhitelist: Set<Class<out Block>> = setOf(
        SkullBlock::class.java, WallSkullBlock::class.java,
        FlowerPotBlock::class.java, LadderBlock::class.java, VineBlock::class.java,
    )

    private val feetPassableWhitelist: Set<Class<out Block>> = passableWhitelist + setOf(
        BasePressurePlateBlock::class.java,
    )

    private val landingBlacklist: Set<Class<out Block>> = setOf(
        EndPortalBlock::class.java,
    )

    private fun Block.matchesAny(whitelist: Set<Class<out Block>>): Boolean =
        whitelist.any { it.isInstance(this) }

    private val blockFlags: IntArray = IntArray(Block.BLOCK_STATE_REGISTRY.size()).apply {
        Block.BLOCK_STATE_REGISTRY.forEach { state ->
            val block = state.block
            var flags = 0
            if (block.matchesAny(passableWhitelist)) flags = flags or PASSABLE
            if (block.matchesAny(blocksFeetWhitelist)) flags = flags or BLOCKS_FEET
            if (block.matchesAny(feetPassableWhitelist)) flags = flags or FEET_PASSABLE
            if (block.matchesAny(landingBlacklist)) flags = flags or LANDING_BLACKLIST
            this[Block.getId(state)] = flags
        }
    }
}
