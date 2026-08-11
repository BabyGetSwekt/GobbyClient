package gobby.utils.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.world.VoxelRay
import gobby.utils.PlayerUtils
import gobby.utils.getEtherTransmissionRange
import gobby.utils.rotation.AngleUtils.calcAimAnglesBetween
import net.minecraft.core.Direction
import net.minecraft.core.SectionPos
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object EtherwarpUtils {

    private val AIR = Blocks.AIR.defaultBlockState()

    val TARGET_BLOCKS = setOf(
        Blocks.PRISMARINE_BRICK_SLAB,
        Blocks.PRISMARINE_BRICK_STAIRS,
        Blocks.PRISMARINE_BRICKS,
        Blocks.PRISMARINE_WALL
    )

    private val AIM_OFFSETS = listOf(
        Triple(0.5, 0.5, 0.5),
        Triple(0.5, 0.95, 0.5),
        Triple(0.5, 0.5, 0.05), Triple(0.5, 0.5, 0.95),
        Triple(0.05, 0.5, 0.5), Triple(0.95, 0.5, 0.5),
        Triple(0.5, 0.05, 0.5),
        Triple(0.05, 0.95, 0.05), Triple(0.95, 0.95, 0.05), Triple(0.05, 0.95, 0.95), Triple(0.95, 0.95, 0.95),
        Triple(0.05, 0.5, 0.05), Triple(0.95, 0.5, 0.05), Triple(0.05, 0.5, 0.95), Triple(0.95, 0.5, 0.95),
        Triple(0.05, 0.05, 0.05), Triple(0.95, 0.05, 0.05), Triple(0.05, 0.05, 0.95), Triple(0.95, 0.05, 0.95)
    )

    fun currentRange(): Double = mc.player?.mainHandItem?.getEtherTransmissionRange()?.toDouble() ?: 0.0

    fun aimForBlock(target: BlockPos, eye: Vec3): Pair<Float, Float>? = aimForBlock(target, eye, currentRange())

    fun aimForBlock(target: BlockPos, eye: Vec3, range: Double, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): Pair<Float, Float>? {
        if (range <= 0.0) return null
        val hits = AIM_OFFSETS.filter { (ox, oy, oz) -> rayReaches(eye, Vec3(target.x + ox, target.y + oy, target.z + oz), target, range, cached, snapshot) }
        if (hits.isEmpty()) return null
        val centroid = Vec3(target.x + hits.sumOf { it.first } / hits.size, target.y + hits.sumOf { it.second } / hits.size, target.z + hits.sumOf { it.third } / hits.size)
        val aimPoint = if (rayReaches(eye, centroid, target, range, cached, snapshot)) centroid
            else hits.first().let { Vec3(target.x + it.first, target.y + it.second, target.z + it.third) }
        return calcAimAnglesBetween(eye, aimPoint)
    }

    fun quickAim(target: BlockPos, eye: Vec3, range: Double, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): Pair<Float, Float>? {
        if (range <= 0.0) return null
        return AIM_OFFSETS.firstNotNullOfOrNull { (ox, oy, oz) ->
            val point = Vec3(target.x + ox, target.y + oy, target.z + oz)
            if (rayReaches(eye, point, target, range, cached, snapshot)) calcAimAnglesBetween(eye, point) else null
        }
    }

    private fun rayReaches(eye: Vec3, point: Vec3, target: BlockPos, range: Double, cached: Boolean, snapshot: BlockCache.SnapshotView?): Boolean {
        val dir = point.subtract(eye)
        val dist = dir.length()
        if (dist !in MIN_AIM_DISTANCE..range) return false
        val hit = traverseVoxels(eye, dir.scale(range / dist), etherWarp = true, cached = cached, snapshot = snapshot)
        return hit.succeeded && hit.pos == target
    }

    fun nearestEtherwarpable(
        center: BlockPos,
        cached: Boolean = false,
        snapshot: BlockCache.SnapshotView? = null,
        accept: (BlockPos) -> Boolean = { true }
    ): BlockPos? {
        val snap = if (cached) snapshot ?: BlockCache.freeze() else null
        return GOAL_OFFSETS.asSequence()
            .map { center.offset(it.x, it.y, it.z) }
            .firstOrNull { accept(it) && isEtherwarpable(it, cached, snap) }
    }

    fun isEtherwarpable(pos: BlockPos, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): Boolean {
        val level = mc.level ?: return false
        val snap = if (cached) snapshot ?: BlockCache.freeze() else null
        val getter = if (cached) EmptyBlockGetter.INSTANCE else level
        fun stateAt(p: BlockPos): BlockState = snap?.getBlockState(p) ?: level.getBlockState(p)
        val state = stateAt(pos)
        val id = Block.getId(state)
        if ((blockFlags[id] and PASSABLE) != 0 || blockFlags[id] hasFlag LANDING_BLACKLIST) return false
        val shape = state.getCollisionShape(getter, pos)
        if (shape.isEmpty) return false
        val feetY = pos.y + max(1, ceil(shape.max(Direction.Axis.Y)).toInt())
        if (!isValidFeetSpot(blockFlags[Block.getId(stateAt(BlockPos(pos.x, feetY, pos.z)))])) return false
        return isValidHeadSpot(blockFlags[Block.getId(stateAt(BlockPos(pos.x, feetY + 1, pos.z)))])
    }

    data class EtherPos(val succeeded: Boolean, val pos: BlockPos?, val state: BlockState? = null) {
        val vec3: Vec3 get() = Vec3(pos ?: BlockPos.ZERO)

        companion object {
            val NONE = EtherPos(false, null)
        }
    }

    fun getEtherPos(eyeHeight: Double? = null): EtherPos {
        val range = mc.player?.mainHandItem?.getEtherTransmissionRange()?.toDouble() ?: 0.0
        return getEtherPos(mc.player?.position(), range, etherWarp = true, eyeHeight = eyeHeight)
    }

    fun getEtherPos(
        position: Vec3?,
        distance: Double,
        returnEnd: Boolean = false,
        etherWarp: Boolean = false,
        eyeHeight: Double? = null
    ): EtherPos {
        val player = mc.player ?: return EtherPos.NONE
        if (position == null) return EtherPos.NONE

        val startPos = position.add(0.0, eyeHeight ?: PlayerUtils.getEyeHeight(), 0.0)
        val ray = player.lookAngle.multiply(distance, distance, distance)

        return traverseVoxels(startPos, ray, etherWarp)
            .takeUnless { it == EtherPos.NONE && returnEnd }
            ?: EtherPos(true, BlockPos.containing(startPos.add(ray)), null)
    }

    fun etherwarpRaycast(eye: Vec3, rayX: Double, rayY: Double, rayZ: Double, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): EtherPos =
        traverseVoxels(eye, rayX, rayY, rayZ, etherWarp = true, cached = cached, snapshot = snapshot)

    fun etherwarpRaycast(eye: Vec3, ray: Vec3, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): EtherPos =
        etherwarpRaycast(eye, ray.x, ray.y, ray.z, cached, snapshot)

    fun validateAim(eye: Vec3, target: BlockPos, range: Double, aim: Pair<Float, Float>): EtherPos {
        val direction = directionFromAngles(aim.first, aim.second)
        return etherwarpRaycast(eye, direction.scale(range))
            .takeIf { it.succeeded && it.pos == target } ?: EtherPos.NONE
    }

    fun directionFromAngles(yaw: Float, pitch: Float): Vec3 {
        val yawRadians = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())
        val horizontal = cos(pitchRadians)
        return Vec3(-sin(yawRadians) * horizontal, -sin(pitchRadians), cos(yawRadians) * horizontal)
    }

    fun isEtherwarpBlacklisted(state: BlockState?): Boolean {
        if (state == null) return true
        val block = state.block
        val isBottomSlab = block is SlabBlock && state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
        return isBottomSlab || block is CarpetBlock || block is WallBlock || block is FenceBlock ||
            block is FenceGateBlock || block is HopperBlock || block is CauldronBlock || block is BannerBlock
    }

    fun explainAimFailure(eye: Vec3, target: BlockPos, range: Double, snapshot: BlockCache.SnapshotView?): String =
        AIM_OFFSETS.joinToString(" | ") { (ox, oy, oz) ->
            val point = Vec3(target.x + ox, target.y + oy, target.z + oz)
            val direction = point.subtract(eye)
            val distance = direction.length()
            if (distance !in MIN_AIM_DISTANCE..range) {
                "offset=$ox,$oy,$oz out-of-range"
            } else {
                val ray = direction.scale(range / distance)
                val cached = traverseVoxels(eye, ray, etherWarp = true, cached = true, snapshot = snapshot)
                val live = traverseVoxels(eye, ray, etherWarp = true, cached = false)
                "offset=$ox,$oy,$oz cached=${describe(cached)} live=${describe(live)}"
            }
        }

    fun hopDiagnostic(eye: Vec3, target: BlockPos, aim: Pair<Float, Float>, range: Double, snapshot: BlockCache.SnapshotView?): String {
        val dir = directionFromAngles(aim.first, aim.second)
        val storedRay = etherwarpRaycast(eye, eye.add(dir.scale(range)))
        val cachedState = (snapshot ?: BlockCache.freeze()).getBlockState(target)
        val liveState = mc.level?.getBlockState(target)
        return "\n  eye=(${eye.x},${eye.y},${eye.z})" +
            "\n  target=$target" +
            "\n  storedYaw=${aim.first} storedPitch=${aim.second} range=$range" +
            "\n  storedRayHit=${storedRay.pos} storedRayBlock=${blockName(storedRay.state)} storedRaySucceeded=${storedRay.succeeded}" +
            "\n  cachedTargetState=${blockName(cachedState)} liveTargetState=${blockName(liveState)}"
    }

    fun stateComparison(pos: BlockPos): String =
        "cached=${blockName(BlockCache.freeze().getBlockState(pos))} live=${blockName(mc.level?.getBlockState(pos))}"

    private fun describe(result: EtherPos): String =
        "success=${result.succeeded},pos=${result.pos ?: "none"},block=${blockName(result.state)}"

    private fun blockName(state: BlockState?): String =
        state?.let { BuiltInRegistries.BLOCK.getKey(it.block).toString() } ?: "none"

    /**
     * DDA voxel raycaster which I adapted from Odin.
     * Original DDA logic by UnclaimedBloom6, integrated into Odin by Odtheking under BSD 3-Clause license.
     * This function has been modified by me.
     * Source: https://github.com/odtheking/Odin/blob/77b66713f74849bbcc05067484e6e85c01c96698/src/main/kotlin/com/odtheking/odin/features/impl/render/Etherwarp.kt#L142
     */
    private fun traverseVoxels(start: Vec3, ray: Vec3, etherWarp: Boolean, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): EtherPos =
        traverseVoxels(start, ray.x, ray.y, ray.z, etherWarp, cached, snapshot)

    private fun traverseVoxels(start: Vec3, rayX: Double, rayY: Double, rayZ: Double, etherWarp: Boolean, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): EtherPos {
        val world = mc.level ?: return EtherPos.NONE
        val minY = world.minY
        val maxY = world.maxY
        val blockGetter = if (cached) EmptyBlockGetter.INSTANCE else world

        val ray = VoxelRay.threadLocal(start.x, start.y, start.z, rayX, rayY, rayZ)
        val cursor = BlockPos.MutableBlockPos()
        val cachedSource = if (cached) snapshot ?: BlockCache.freeze() else null
        var cachedChunkX = SectionPos.blockToSectionCoord(ray.x)
        var cachedChunkZ = SectionPos.blockToSectionCoord(ray.z)
        var chunk = if (cached) null else world.getChunk(cachedChunkX, cachedChunkZ)
        fun stateAt(bx: Int, by: Int, bz: Int): BlockState {
            cursor.set(bx, by, bz)
            if (cached) return cachedSource!!.getBlockState(cursor)
            val scx = SectionPos.blockToSectionCoord(bx)
            val scz = SectionPos.blockToSectionCoord(bz)
            if (scx != cachedChunkX || scz != cachedChunkZ) {
                chunk = world.getChunk(scx, scz)
                cachedChunkX = scx
                cachedChunkZ = scz
            }
            if (BlockCache.isPassableOverride(cursor)) return AIR
            return chunk!!.getBlockState(cursor)
        }

        repeat(1000) {
            val x = ray.x
            val y = ray.y
            val z = ray.z
            if (y !in minY..<maxY) return EtherPos.NONE
            if (cachedSource != null && !cachedSource.hasSnapshot(x, z)) {
                val cx = SectionPos.blockToSectionCoord(x)
                val cz = SectionPos.blockToSectionCoord(z)
                if (BlockCache.reportMissingSnapshotChunk(cx, cz)) println("[GobbyCache] etherwarp raycast missing snapshot chunk=$cx,$cz")
                return EtherPos.NONE
            }

            val state = stateAt(x, y, z)
            val id = Block.getId(state)
            val flags = blockFlags[id]
            val isPassable = (flags and PASSABLE) != 0

            if ((etherWarp && !isPassable) || (!etherWarp && id != 0)) {
                val hit = BlockPos(x, y, z)
                if (!etherWarp && isPassable) return EtherPos(false, hit, state)
                val shape = state.getCollisionShape(blockGetter, hit)
                if (shape.isEmpty) return EtherPos(false, hit, state)
                if (flags hasFlag LANDING_BLACKLIST) return EtherPos(false, hit, state)
                val collisionTop = shape.max(Direction.Axis.Y)
                val clearanceBaseY = hit.y + max(1, ceil(collisionTop).toInt())
                val feetFlags = blockFlags[Block.getId(stateAt(hit.x, clearanceBaseY, hit.z))]
                if (!isValidFeetSpot(feetFlags)) return EtherPos(false, hit, state)
                val headFlags = blockFlags[Block.getId(stateAt(hit.x, clearanceBaseY + 1, hit.z))]
                if (!isValidHeadSpot(headFlags)) return EtherPos(false, hit, state)
                return EtherPos(true, hit, state)
            }

            if (ray.atEnd) return EtherPos.NONE
            ray.advance()
        }

        return EtherPos.NONE
    }

    private infix fun Int.hasFlag(bit: Int): Boolean = (this and bit) != 0

    private fun isValidFeetSpot(flags: Int): Boolean =
        flags hasFlag FEET_PASSABLE && !(flags hasFlag BLOCKS_FEET) && !(flags hasFlag LANDING_BLACKLIST)

    private fun isValidHeadSpot(flags: Int): Boolean =
        flags hasFlag PASSABLE && !(flags hasFlag BLOCKS_FEET)

    private const val GOAL_XZ_RADIUS = 10
    private const val GOAL_UP = 3
    private const val GOAL_DOWN = 22
    private val GOAL_OFFSETS: List<BlockPos> = (-GOAL_XZ_RADIUS..GOAL_XZ_RADIUS).let { h ->
        h.flatMap { dx -> h.flatMap { dz -> (-GOAL_DOWN..GOAL_UP).map { dy -> BlockPos(dx, dy, dz) } } }
            .sortedBy { it.x * it.x + it.y * it.y + it.z * it.z }
    }

    private const val MIN_AIM_DISTANCE = 1e-4
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

    private fun classify(block: Block): Int {
        var flags = 0
        if (block.matchesAny(passableWhitelist)) flags = flags or PASSABLE
        if (block.matchesAny(blocksFeetWhitelist)) flags = flags or BLOCKS_FEET
        if (block.matchesAny(feetPassableWhitelist)) flags = flags or FEET_PASSABLE
        if (block.matchesAny(landingBlacklist)) flags = flags or LANDING_BLACKLIST
        return flags
    }

    private val blockFlags: IntArray = IntArray(Block.BLOCK_STATE_REGISTRY.size()).also { flags ->
        val perBlock = HashMap<Block, Int>()
        Block.BLOCK_STATE_REGISTRY.forEach { state ->
            flags[Block.getId(state)] = perBlock.getOrPut(state.block) { classify(state.block) }
        }
    }
}
