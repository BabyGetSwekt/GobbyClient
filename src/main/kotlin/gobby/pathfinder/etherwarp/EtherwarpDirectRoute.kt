package gobby.pathfinder.etherwarp

import gobby.pathfinder.world.BlockCache
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import kotlin.math.max

internal object EtherwarpDirectRoute {
    fun preload() = Unit

    fun find(
        from: Vec3,
        goal: BlockPos,
        kind: EtherwarpKind,
        config: EtherwarpPathConfig,
        reached: (BlockPos) -> Boolean,
        snapshot: BlockCache.SnapshotView
    ): List<EtherwarpNode>? {
        val range = kind.searchRange(config)
        val startPos = BlockPos.containing(from)
        val eye = Vec3(from.x, from.y + kind.eyeHeight(), from.z)
        val access = EtherwarpUtils.cachedAccess(snapshot) ?: return null
        val aim = if (kind == EtherwarpKind.ETHERWARP) {
            EtherwarpUtils.quickAim(goal, eye, range, access)?.let { Aim(it.first, it.second) }
        } else {
            kind.aimAt(eye, goal, range, snapshot = snapshot)
        } ?: return null
        val direction = EtherwarpUtils.directionFromAngles(aim.yaw, aim.pitch)
        val hit = kind.hit(eye, direction.x * range, direction.y * range, direction.z * range, goal, access) ?: return null
        if (!reached(hit)) return null
        if (hit.y > maxLandingY(kind, startPos.y, goal.y)) return null
        val start = EtherwarpNode(from.x, from.y, from.z, startPos, ZERO_SCORE, ZERO_SCORE, null, aim.yaw, aim.pitch)
        val landing = EtherwarpNode(
            hit.x + 0.5,
            kind.landingY(hit.y),
            hit.z + 0.5,
            hit,
            kind.hopCost(hit),
            ZERO_SCORE,
            null,
            ZERO_AIM,
            ZERO_AIM
        )
        return listOf(start, landing).takeIf { snapshot.missingChunksAccessed().isEmpty() }
    }

    private const val ZERO_SCORE = 0.0
    private const val ZERO_AIM = 0f
}
