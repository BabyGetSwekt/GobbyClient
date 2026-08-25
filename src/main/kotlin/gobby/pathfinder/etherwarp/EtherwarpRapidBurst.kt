package gobby.pathfinder.etherwarp

import gobby.utils.PlayerUtils
import gobby.utils.findEtherwarpableHotbarSlot
import gobby.utils.managers.PacketOrderManager
import gobby.utils.managers.SwapManager
import gobby.utils.rotation.RotationUtils
import gobby.utils.rotation.ServerRotationLeaseManager
import gobby.pathfinder.world.BlockCache
import net.minecraft.client.player.LocalPlayer

internal class EtherwarpRapidBurst(
    private val generation: () -> Long,
    private val path: () -> List<EtherwarpNode>,
    private val kind: () -> EtherwarpKind,
    private val mode: () -> EtherwarpExecutionMode,
    private val executionVariance: () -> EtherwarpExecutionVariance,
    private val isItemHeld: () -> Boolean,
    private val onCast: (Int, EtherwarpNode, Aim, LocalPlayer) -> Unit,
    private val onComplete: () -> Unit,
    private val onAbort: (String) -> Unit,
    private val snapshot: () -> BlockCache.SnapshotView?
) {
    private var scheduled = false
    private var completed = false
    private var castIndex = 0
    private var nextCastNanos = 0L
    private var queued: PacketOrderManager.Registration? = null
    private var expectedGeneration = Long.MIN_VALUE
    private var preparedAims: List<Aim> = emptyList()

    fun active(): Boolean = mode() != EtherwarpExecutionMode.AWAIT_TELEPORT && !completed

    fun reset() {
        queued?.let(PacketOrderManager::cancel)
        queued = null
        scheduled = false
        completed = false
        castIndex = 0
        nextCastNanos = 0L
        expectedGeneration = Long.MIN_VALUE
        preparedAims = emptyList()
    }

    fun tick(player: LocalPlayer) {
        if (!active() || !ready(player)) return
        if (!scheduled) {
            schedule(player)
            return
        }
        queueNextCast(player)
    }

    private fun ready(player: LocalPlayer): Boolean {
        val currentKind = kind()
        if (player.lastSentInput?.shift() != currentKind.sneak || (currentKind.sneak && !player.isCrouching)) return false
        if (!SwapManager.canUseAbility) return false
        if (isItemHeld()) return true
        if (findEtherwarpableHotbarSlot() < 0) return abort(NO_TELEPORT_ITEM)
        SwapManager.swapToEtherwarpableItem()
        return false
    }

    private fun schedule(player: LocalPlayer) {
        val route = path()
        if (route.size < 2) {
            abort(INVALID_ROUTE)
            return
        }
        preparedAims = prepareAims(route)
        expectedGeneration = generation()
        scheduled = true
        castIndex = 0
        nextCastNanos = 0L
        queueNextCast(player)
    }

    private fun queueNextCast(player: LocalPlayer) {
        val route = path()
        if (queued != null || castIndex !in 0 until route.lastIndex || System.nanoTime() < nextCastNanos) return
        if (!isCurrent() || !isItemHeld()) {
            abort(ITEM_CHANGED)
            return
        }
        val source = route[castIndex]
        val target = route[castIndex + 1]
        val aim = resolveAim(source, target, castIndex, preparedAims[castIndex]) ?: run {
            abort(NEXT_NODE_NOT_AIMABLE)
            return
        }
        val appliedAim = applyModeRotation(aim, player) ?: return
        val generationAtQueue = expectedGeneration
        queued = PacketOrderManager.register(PacketOrderManager.Phase.ITEM_USE, Runnable {
            queued = null
            if (!isCurrent(generationAtQueue) || castIndex !in 0 until route.lastIndex || !isItemHeld()) return@Runnable
            val livePlayer = playerOrAbort() ?: return@Runnable
            if (!PlayerUtils.useItem(appliedAim.yaw, appliedAim.pitch)) {
                abort(RAPID_CAST_FAILED)
                return@Runnable
            }
            onCast(castIndex, target, appliedAim, livePlayer)
            castIndex++
            nextCastNanos = System.nanoTime() + EtherwarpExecutionSettings.boundedRapidSpacing()
            if (castIndex == route.lastIndex) {
                completed = true
                onComplete()
            }
        })
    }

    private fun resolveAim(source: EtherwarpNode, target: EtherwarpNode, index: Int, prepared: Aim? = null): Aim? {
        val base = resolveBaseAim(source, target, prepared) ?: return null
        val currentKind = kind()
        if (currentKind != EtherwarpKind.ETHERWARP) return base
        if (!executionVariance().enabled) return base
        val range = gobby.utils.skyblock.EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: currentKind.defaultRange
        val eye = source.eye.add(0.0, currentKind.eyeHeight(), 0.0)
        val planningSnapshot = snapshot()
        val access = planningSnapshot?.let { gobby.utils.skyblock.EtherwarpUtils.cachedAccess(it) }
            ?: gobby.utils.skyblock.EtherwarpUtils.liveOrCachedAccess()
            ?: return null
        return executionVariance().resolve(base, index, eye, target.pos, range, access)
    }

    private fun resolveBaseAim(source: EtherwarpNode, target: EtherwarpNode, prepared: Aim?): Aim? {
        val currentKind = kind()
        val range = gobby.utils.skyblock.EtherwarpUtils.currentRange().takeIf { it > 0.0 } ?: currentKind.defaultRange
        val eye = source.eye.add(0.0, currentKind.eyeHeight(), 0.0)
        if (currentKind != EtherwarpKind.ETHERWARP) return currentKind.aimAt(eye, target.pos, range, cached = false)
        val stored = prepared ?: Aim(source.yaw, source.pitch)
        val planningSnapshot = snapshot()
        val access = if (planningSnapshot != null) {
            gobby.utils.skyblock.EtherwarpUtils.cachedAccess(planningSnapshot)
        } else {
            gobby.utils.skyblock.EtherwarpUtils.liveOrCachedAccess()
        } ?: return null
        val base = gobby.utils.skyblock.EtherwarpUtils.validateAim(eye, target.pos, range, stored.yaw to stored.pitch, access)
            .takeIf { it != gobby.utils.skyblock.EtherwarpUtils.EtherPos.NONE }?.let { stored }
            ?: (planningSnapshot?.let { gobby.utils.skyblock.EtherwarpUtils.aimForBlock(target.pos, eye, range, cached = true, snapshot = it) }
                ?: gobby.utils.skyblock.EtherwarpUtils.aimForBlock(target.pos, eye, range, access))?.let { Aim(it.first, it.second) }
            ?: return null
        return base
    }

    private fun prepareAims(route: List<EtherwarpNode>): List<Aim> =
        route.dropLast(1).map { Aim(it.yaw, it.pitch) }

    private fun applyModeRotation(aim: Aim, player: LocalPlayer): Aim? = when (mode()) {
        EtherwarpExecutionMode.ROTATE -> {
            val yaw = RotationUtils.nearestEquivalentYaw(aim.yaw, player.yRot)
            RotationUtils.snapTo(yaw, aim.pitch)
            player.yHeadRot = yaw
            player.yBodyRot = yaw
            Aim(yaw, aim.pitch)
        }
        EtherwarpExecutionMode.PACKET, EtherwarpExecutionMode.SERVER_ROTATE ->
            ServerRotationLeaseManager.request(OWNER, aim.yaw, aim.pitch, LEASE_TICKS, PRIORITY)?.let { aim }
        EtherwarpExecutionMode.AWAIT_TELEPORT -> abort(RAPID_MODE_CHANGED).let { null }
    }

    private fun playerOrAbort(): LocalPlayer? =
        gobby.Gobbyclient.mc.player ?: run {
            abort(PLAYER_UNAVAILABLE)
            null
        }

    private fun isCurrent(): Boolean = isCurrent(expectedGeneration)

    private fun isCurrent(value: Long): Boolean = value == generation() && path().isNotEmpty()

    private fun abort(reason: String): Boolean {
        onAbort(reason)
        return false
    }

    private companion object {
        const val OWNER = ServerRotationLeaseManager.PATHFINDER_OWNER
        const val PRIORITY = ServerRotationLeaseManager.PATHFINDER_PRIORITY
        const val LEASE_TICKS = 1
        const val NO_TELEPORT_ITEM = "No Aspect of the Void/End found"
        const val ITEM_CHANGED = "Teleport item changed during rapid route"
        const val NEXT_NODE_NOT_AIMABLE = "Next node not aimable live"
        const val PLAYER_UNAVAILABLE = "Player unavailable during rapid route"
        const val RAPID_CAST_FAILED = "Rapid cast could not be sent"
        const val RAPID_MODE_CHANGED = "Rapid route entered await-teleport mode"
        const val INVALID_ROUTE = "Rapid route has invalid geometry"
    }
}
