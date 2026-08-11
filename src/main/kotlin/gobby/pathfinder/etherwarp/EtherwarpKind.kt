package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.utils.PlayerUtils
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.min

private const val ETHERWARP_RANGE = 61.0
private const val ETHERWARP_SEARCH_RANGE = 55.0
private const val TRANSMISSION_RANGE = 12.0
private const val ETHERWARP_STAND_OFFSET = 1.05
private const val TRANSMISSION_STAND_OFFSET = 0.0
private const val AIR_LANDING_PENALTY = 50.0

enum class EtherwarpKind(
    val sneak: Boolean,
    val defaultRange: Double,
    private val standOffset: Double
) {
    ETHERWARP(true, ETHERWARP_RANGE, ETHERWARP_STAND_OFFSET) {
        override fun hit(eye: Vec3, rayX: Double, rayY: Double, rayZ: Double, goal: BlockPos, snapshot: BlockCache.SnapshotView?): BlockPos? {
            val result = EtherwarpUtils.etherwarpRaycast(eye, rayX, rayY, rayZ, cached = true, snapshot = snapshot)
            val pos = result.pos ?: return null
            return if (result.succeeded && (pos == goal || !EtherwarpUtils.isEtherwarpBlacklisted(result.state))) pos else null
        }

        override fun searchRange(config: EtherwarpPathConfig): Double = min(config.etherRange, ETHERWARP_SEARCH_RANGE)

        override fun resolveAim(eye: Vec3, hit: BlockPos, range: Double, rayYaw: Float, rayPitch: Float): Aim = Aim(rayYaw, rayPitch)

        override fun aimAt(eye: Vec3, target: BlockPos, range: Double, cached: Boolean, snapshot: BlockCache.SnapshotView?): Aim? =
            EtherwarpUtils.quickAim(target, eye, range, cached = cached, snapshot = snapshot)?.let { Aim(it.first, it.second) }
    },
    TRANSMISSION(false, TRANSMISSION_RANGE, TRANSMISSION_STAND_OFFSET) {
        override fun hit(eye: Vec3, rayX: Double, rayY: Double, rayZ: Double, goal: BlockPos, snapshot: BlockCache.SnapshotView?): BlockPos? =
            EtherwarpRaycaster.transmission(eye, Vec3(rayX, rayY, rayZ))

        override fun landingCost(hit: BlockPos): Double = if (BlockCache.isSolid(hit.below())) 0.0 else AIR_LANDING_PENALTY

        override fun goal(to: BlockPos): BlockPos = to.above()
    };

    abstract fun hit(eye: Vec3, rayX: Double, rayY: Double, rayZ: Double, goal: BlockPos, snapshot: BlockCache.SnapshotView? = null): BlockPos?

    open fun searchRange(config: EtherwarpPathConfig): Double = defaultRange

    open fun resolveAim(eye: Vec3, hit: BlockPos, range: Double, rayYaw: Float, rayPitch: Float): Aim? = Aim(rayYaw, rayPitch)

    open fun aimAt(eye: Vec3, target: BlockPos, range: Double, cached: Boolean = true, snapshot: BlockCache.SnapshotView? = null): Aim? = direction(eye, target, range, snapshot)

    open fun landingCost(hit: BlockPos): Double = 0.0

    open fun goal(to: BlockPos): BlockPos = to

    fun eyeHeight(): Double = PlayerUtils.getEyeHeight(sneak)

    fun landingY(hitY: Int): Double = hitY + standOffset

    fun direction(from: Vec3, to: BlockPos, range: Double, snapshot: BlockCache.SnapshotView? = null): Aim? =
        EtherwarpRaycaster.aim(from, to, range, this, snapshot)
}
