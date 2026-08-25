package gobby.utils.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.pathfinder.world.BlockCache
import gobby.pathfinder.world.SnapshotCursor
import gobby.pathfinder.world.VoxelRay
import gobby.utils.PlayerUtils
import gobby.utils.getEtherTransmissionRange
import gobby.utils.rotation.AngleUtils.calcAimAnglesBetween
import net.minecraft.core.Direction
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.piston.PistonHeadBlock
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object EtherwarpUtils {

    private val AIR = Blocks.AIR.defaultBlockState()

    val TARGET_BLOCKS = setOf(
        Blocks.PRISMARINE_BRICK_SLAB,
        Blocks.PRISMARINE_BRICK_STAIRS,
        Blocks.PRISMARINE_BRICKS,
        Blocks.PRISMARINE_WALL
    )

    private val AIM_OFFSETS = arrayOf(
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
        val access = worldAccess(cached, snapshot) ?: return null
        return aimForBlock(target, eye, range, access)
    }

    fun aimForBlock(target: BlockPos, eye: Vec3, range: Double, access: EtherwarpWorldAccess): Pair<Float, Float>? {
        if (range <= 0.0) return null
        var hitCount = 0
        var sumX = 0.0
        var sumY = 0.0
        var sumZ = 0.0
        var firstHit: Triple<Double, Double, Double>? = null
        var index = 0
        while (index < AIM_OFFSETS.size) {
            val offset = AIM_OFFSETS[index]
            if (rayReaches(eye, target, offset.first, offset.second, offset.third, range, access)) {
                firstHit = firstHit ?: offset
                hitCount++
                sumX += offset.first
                sumY += offset.second
                sumZ += offset.third
            }
            index++
        }
        if (hitCount == 0) return topFaceAim(target, eye, range, access)
        val centroid = Vec3(target.x + sumX / hitCount, target.y + sumY / hitCount, target.z + sumZ / hitCount)
        aimAtPoint(eye, centroid, target, range, access)?.let { return it }
        val fallback = firstHit ?: return null
        return aimAtPoint(eye, Vec3(target.x + fallback.first, target.y + fallback.second, target.z + fallback.third), target, range, access)
    }

    fun quickAim(target: BlockPos, eye: Vec3, range: Double, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): Pair<Float, Float>? {
        val access = worldAccess(cached, snapshot) ?: return null
        return quickAim(target, eye, range, access)
    }

    fun quickAim(target: BlockPos, eye: Vec3, range: Double, access: EtherwarpWorldAccess): Pair<Float, Float>? {
        if (range <= 0.0) return null
        topFaceAim(target, eye, range, access)?.let { return it }
        var index = 0
        while (index < AIM_OFFSETS.size) {
            val (ox, oy, oz) = AIM_OFFSETS[index]
            aimAtPoint(eye, Vec3(target.x + ox, target.y + oy, target.z + oz), target, range, access)?.let { return it }
            index++
        }
        return null
    }

    private fun topFaceAim(target: BlockPos, eye: Vec3, range: Double, access: EtherwarpWorldAccess): Pair<Float, Float>? {
        val topFace = target.y + 1.0
        if (eye.y <= topFace) return null
        return aimAtPoint(eye, Vec3(target.x + 0.5, topFace - TOP_FACE_EPSILON, target.z + 0.5), target, range, access)
    }

    private fun aimAtPoint(eye: Vec3, point: Vec3, target: BlockPos, range: Double, access: EtherwarpWorldAccess): Pair<Float, Float>? {
        if (!rayReaches(eye, point, target, range, access)) return null
        val angles = calcAimAnglesBetween(eye, point)
        return angles.takeIf { validateAim(eye, target, range, it, access) != EtherPos.NONE }
    }

    private fun rayReaches(eye: Vec3, point: Vec3, target: BlockPos, range: Double, access: EtherwarpWorldAccess): Boolean {
        val dir = point.subtract(eye)
        val dist = dir.length()
        if (dist !in MIN_AIM_DISTANCE..range) return false
        val hit = etherwarpRaycast(eye, dir.scale(range / dist), access)
        return hit.succeeded && hit.pos == target
    }

    private fun rayReaches(
        eye: Vec3,
        target: BlockPos,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        range: Double,
        access: EtherwarpWorldAccess
    ): Boolean {
        val dx = target.x + offsetX - eye.x
        val dy = target.y + offsetY - eye.y
        val dz = target.z + offsetZ - eye.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        if (distance !in MIN_AIM_DISTANCE..range) return false
        val hit = etherwarpRaycast(eye, dx * range / distance, dy * range / distance, dz * range / distance, access)
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
        val access = worldAccess(cached, snapshot) ?: return false
        return isEtherwarpable(pos, access)
    }

    fun isEtherwarpable(pos: BlockPos, access: EtherwarpWorldAccess): Boolean {
        val state = access.blockSource(pos) ?: return false
        val id = Block.getId(state)
        if ((blockFlags[id] and PASSABLE) != 0 || blockFlags[id] hasFlag LANDING_BLACKLIST) return false
        val shape = state.getCollisionShape(access.collisionGetter, pos)
        if (shape.isEmpty) return false
        val feetY = pos.y + max(1, ceil(shape.max(Direction.Axis.Y)).toInt())
        val feet = access.blockSource(BlockPos(pos.x, feetY, pos.z)) ?: return false
        val head = access.blockSource(BlockPos(pos.x, feetY + 1, pos.z)) ?: return false
        if (!isValidFeetSpot(blockFlags[Block.getId(feet)])) return false
        return isValidHeadSpot(blockFlags[Block.getId(head)])
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

        val access = worldAccess(false, null) ?: return EtherPos.NONE
        return raycast(startPos, ray, etherWarp, access)
            .takeUnless { it == EtherPos.NONE && returnEnd }
            ?: EtherPos(true, BlockPos.containing(startPos.add(ray)), null)
    }

    fun etherwarpRaycast(eye: Vec3, rayX: Double, rayY: Double, rayZ: Double, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): EtherPos =
        worldAccess(cached, snapshot)?.let { etherwarpRaycast(eye, Vec3(rayX, rayY, rayZ), it) } ?: EtherPos.NONE

    fun etherwarpRaycast(eye: Vec3, ray: Vec3, cached: Boolean = false, snapshot: BlockCache.SnapshotView? = null): EtherPos =
        worldAccess(cached, snapshot)?.let { etherwarpRaycast(eye, ray, it) } ?: EtherPos.NONE

    fun etherwarpRaycast(eye: Vec3, ray: Vec3, access: EtherwarpWorldAccess): EtherPos =
        raycast(eye, ray, true, access)

    fun etherwarpRaycast(eye: Vec3, rayX: Double, rayY: Double, rayZ: Double, access: EtherwarpWorldAccess): EtherPos =
        traverseVoxels(eye, rayX, rayY, rayZ, true, access.minY, access.maxY, access.blockSource, access.coordinateSourceFactory, access.collisionGetter)

    private fun raycast(eye: Vec3, ray: Vec3, etherWarp: Boolean, access: EtherwarpWorldAccess): EtherPos =
        traverseVoxels(eye, ray.x, ray.y, ray.z, etherWarp, access.minY, access.maxY, access.blockSource, access.coordinateSourceFactory, access.collisionGetter)

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
                val cached = worldAccess(true, snapshot)?.let { etherwarpRaycast(eye, ray, it) } ?: EtherPos.NONE
                val live = worldAccess(false, null)?.let { etherwarpRaycast(eye, ray, it) } ?: EtherPos.NONE
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

    private fun traverseVoxels(
        start: Vec3,
        rayX: Double,
        rayY: Double,
        rayZ: Double,
        etherWarp: Boolean,
        minY: Int,
        maxY: Int,
        blockSource: (BlockPos) -> BlockState?,
        coordinateSourceFactory: (() -> EtherwarpCoordinateSource)?,
        blockGetter: BlockGetter
    ): EtherPos {

        val ray = VoxelRay.threadLocal(start.x, start.y, start.z, rayX, rayY, rayZ)
        val coordinateSource = coordinateSourceFactory?.invoke()
        val cursor = if (coordinateSource == null) BlockPos.MutableBlockPos() else null

        repeat(MAX_RAY_STEPS) {
            inspectVoxel(ray.x, ray.y, ray.z, etherWarp, minY, maxY, blockSource, coordinateSource, cursor, blockGetter)?.let { return it }
            if (ray.atEnd) return EtherPos.NONE
            ray.advance()
        }

        return EtherPos.NONE
    }

    private fun inspectVoxel(
        x: Int,
        y: Int,
        z: Int,
        etherWarp: Boolean,
        minY: Int,
        maxY: Int,
        blockSource: (BlockPos) -> BlockState?,
        coordinateSource: EtherwarpCoordinateSource?,
        cursor: BlockPos.MutableBlockPos?,
        blockGetter: BlockGetter
    ): EtherPos? {
        if (y !in minY..<maxY) return EtherPos.NONE
        val state = accessState(blockSource, coordinateSource, cursor, x, y, z) ?: return EtherPos.NONE
        val id = Block.getId(state)
        val flags = blockFlags[id]
        val isPassable = (flags and PASSABLE) != 0
        if (!(etherWarp && !isPassable) && (etherWarp || id == 0)) return null
        val hit = BlockPos(x, y, z)
        if (!etherWarp && isPassable) return EtherPos(false, hit, state)
        val shape = state.getCollisionShape(blockGetter, hit)
        if (shape.isEmpty || flags hasFlag LANDING_BLACKLIST) return EtherPos(false, hit, state)
        val clearanceBaseY = hit.y + max(1, ceil(shape.max(Direction.Axis.Y)).toInt())
        val feetState = accessState(blockSource, coordinateSource, cursor, hit.x, clearanceBaseY, hit.z) ?: return EtherPos.NONE
        if (!isValidFeetSpot(blockFlags[Block.getId(feetState)])) return EtherPos(false, hit, state)
        val headState = accessState(blockSource, coordinateSource, cursor, hit.x, clearanceBaseY + 1, hit.z) ?: return EtherPos.NONE
        return if (isValidHeadSpot(blockFlags[Block.getId(headState)])) EtherPos(true, hit, state) else EtherPos(false, hit, state)
    }

    private fun accessState(
        blockSource: (BlockPos) -> BlockState?,
        coordinateSource: EtherwarpCoordinateSource?,
        cursor: BlockPos.MutableBlockPos?,
        x: Int,
        y: Int,
        z: Int
    ): BlockState? = coordinateSource?.stateAt(x, y, z) ?: run {
        val reusablePos = cursor ?: BlockPos.MutableBlockPos()
        reusablePos.set(x, y, z)
        blockSource(reusablePos)
    }

    private infix fun Int.hasFlag(bit: Int): Boolean = (this and bit) != 0

    private fun isValidFeetSpot(flags: Int): Boolean =
        flags hasFlag FEET_PASSABLE && !(flags hasFlag BLOCKS_FEET) && !(flags hasFlag LANDING_BLACKLIST)

    private fun isValidHeadSpot(flags: Int): Boolean =
        flags hasFlag PASSABLE && !(flags hasFlag BLOCKS_FEET)

    fun cachedAccess(snapshot: BlockCache.SnapshotView?): EtherwarpWorldAccess? = worldAccess(true, snapshot)

    fun liveOrCachedAccess(): EtherwarpWorldAccess? {
        val world = mc.level ?: return null
        return EtherwarpWorldAccess(world.minY, world.maxY, { pos ->
            if (BlockCache.isPassableOverride(pos)) AIR else BlockCache.knownStateAt(pos)
        }, world)
    }

    fun liveOrCachedAccess(snapshot: BlockCache.SnapshotView?): EtherwarpWorldAccess? {
        val world = mc.level ?: return null
        val view = snapshot ?: BlockCache.freeze()
        return EtherwarpWorldAccess(world.minY, world.maxY, { pos ->
            when {
                BlockCache.isPassableOverride(pos) -> AIR
                BlockCache.isChunkAvailableLive(pos.x, pos.z) -> world.getBlockState(pos)
                else -> view.stateAt(pos)
            }
        }, world)
    }

    fun validateAim(eye: Vec3, target: BlockPos, range: Double, aim: Pair<Float, Float>, access: EtherwarpWorldAccess): EtherPos {
        val direction = directionFromAngles(aim.first, aim.second)
        return etherwarpRaycast(eye, direction.scale(range), access)
            .takeIf { it.succeeded && it.pos == target } ?: EtherPos.NONE
    }

    private fun worldAccess(cached: Boolean, snapshot: BlockCache.SnapshotView?): EtherwarpWorldAccess? {
        if (cached) {
            val view = snapshot ?: BlockCache.freeze()
            return EtherwarpWorldAccess(view.minY, view.maxY, view::stateAt, coordinateSourceFactory = { SnapshotCursor.threadLocal(view) })
        }
        val world = mc.level ?: return null
        return EtherwarpWorldAccess(world.minY, world.maxY, { pos ->
            if (BlockCache.isPassableOverride(pos)) AIR else world.getBlockState(pos)
        }, world)
    }

    private const val GOAL_XZ_RADIUS = 10
    private const val GOAL_UP = 3
    private const val GOAL_DOWN = 22
    private val GOAL_OFFSETS: List<BlockPos> = (-GOAL_XZ_RADIUS..GOAL_XZ_RADIUS).let { h ->
        h.flatMap { dx -> h.flatMap { dz -> (-GOAL_DOWN..GOAL_UP).map { dy -> BlockPos(dx, dy, dz) } } }
            .sortedBy { it.x * it.x + it.y * it.y + it.z * it.z }
    }

    private const val MIN_AIM_DISTANCE = 1e-4
    private const val MAX_RAY_STEPS = 1000
    private const val TOP_FACE_EPSILON = 0.001
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
        CarpetBlock::class.java,
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
