package gobby.pathfinder.etherwarp

import gobby.Gobbyclient.Companion.mc
import gobby.features.dungeons.RoomPathfinder
import gobby.utils.ChatUtils.modMessage
import gobby.utils.findEtherwarpableHotbarSlot
import gobby.utils.isHoldingSkyblockItem
import gobby.utils.managers.PacketOrderManager
import gobby.utils.managers.SwapManager
import gobby.utils.rotation.ServerRotationLeaseManager
import gobby.utils.skyblock.EtherwarpUtils
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3

object EtherwarpPathExecutor {
    private var nodes: List<EtherwarpNode> = emptyList()
    private var index = 0
    private var kind = EtherwarpKind.ETHERWARP
    private var finishing = false
    private var predictedPosition: Vec3? = null
    private var pathGeneration = 0L
    private var hopField: EtherwarpHopField.Handle? = null
    private var executionSnapshot: gobby.pathfinder.world.BlockCache.SnapshotView? = null
    private val inFlight = ArrayDeque<EtherwarpExecutionHop>()
    private var tickCounter = 0
    private var aimWaitTicks = 0
    private var lastPos: Vec3? = null
    private var clipboardSet = false
    private var reanchors = 0
    private var rapidProgress = EtherwarpRapidProgress(0, 0)
    private var queuedUse: PacketOrderManager.Registration? = null
    private var rotationLease: ServerRotationLeaseManager.Lease? = null

    @Volatile var configuredMode = EtherwarpExecutionMode.AWAIT_TELEPORT
    private var executionMode = EtherwarpExecutionMode.AWAIT_TELEPORT
    private var executionKeepLastServerRotation = false
    private val executionVariance = EtherwarpExecutionVariance()
    private val executionCamera = EtherwarpExecutionCamera()
    private val lifecycle = EtherwarpExecutionLifecycle()
    private val authoritative = EtherwarpExecutionAuthoritative({ nodes }, { inFlight }) { label, actual ->
        lifecycle.progress(label)
        if (executionMode != EtherwarpExecutionMode.AWAIT_TELEPORT) {
            rapidProgress = advanceRapidProgress(rapidProgress, label, tickCounter)
        } else {
            index = nextAwaitExecutionIndex(index, label, nodes.size)
            predictedPosition = actual
            finishing = index >= nodes.size
        }
    }
    private var rotateWaitSequence: Long? = null
    private val rapidBurst = EtherwarpRapidBurst(
        generation = { pathGeneration },
        path = { nodes },
        kind = { kind },
        mode = { executionMode },
        executionVariance = { executionVariance },
        isItemHeld = { isHoldingSkyblockItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END") },
        onCast = ::onRapidCast,
        onComplete = { finishing = true },
        onAbort = ::abort, snapshot = { executionSnapshot }
    )
    private val landingObserver = EtherwarpExecutionLandingObserver(
        route = { nodes },
        kind = { kind },
        inFlight = inFlight,
        lastPosition = { lastPos },
        updateLastPosition = { lastPos = it },
        nextTick = { ++tickCounter },
        reanchor = ::reanchor,
        cancel = ::cancel,
        logLanding = ::logLanding,
        logMiss = ::logMiss,
        onRapidProgress = { label, tick -> rapidProgress = advanceRapidProgress(rapidProgress, label, tick) },
        onProgress = lifecycle::progress,
        shouldRecoverPartial = ::shouldRecoverRapidPartial,
        onPartialRecovery = {
            lifecycle.terminate(EtherwarpExecutionTermination.RAPID_PARTIAL, deferObserver = true)
            modMessage("\u00A7ePartial rapid route, continuing from the confirmed landing")
            cancel()
        }
    )

    @Volatile private var planningSneak = false
    val active: Boolean get() = nodes.isNotEmpty()
    val forceSneak: Boolean get() = planningSneak || (nodes.isNotEmpty() && kind.sneak)

    fun preload() = Unit

    fun beginPlanningSneak() { planningSneak = true }

    fun endPlanningSneak() { planningSneak = false }
    enum class Decision { WAIT, SWAP, CAST, CANCEL }

    internal fun decide(holdingItem: Boolean, swapPossible: Boolean, sneakReady: Boolean, aimValid: Boolean, abilityReady: Boolean = true): Decision = when {
        !holdingItem && !swapPossible -> Decision.CANCEL
        !holdingItem -> Decision.SWAP
        !abilityReady -> Decision.WAIT
        !sneakReady -> Decision.WAIT
        !aimValid -> Decision.CANCEL
        else -> Decision.CAST
    }

    fun start(path: List<EtherwarpNode>, kind: EtherwarpKind, field: EtherwarpHopField.Handle? = null, observer: EtherwarpExecutionObserver? = null, announce: Boolean = true): Boolean {
        cancel()
        RoomPathfinder.missedNode = null
        if (path.size < 2) return false
        nodes = path
        this.kind = kind
        executionMode = configuredMode
        executionKeepLastServerRotation = executionMode == EtherwarpExecutionMode.SERVER_ROTATE && EtherwarpExecutionSettings.keepLastServerRotationEnabled
        executionVariance.start(kind, path.size - 1, EtherwarpExecutionSettings.rotationVarianceEnabled)
        executionSnapshot = if (executionVariance.enabled) gobby.pathfinder.world.BlockCache.freeze() else null
        rotateWaitSequence = EtherwarpServerTickGate.arm(executionMode, EtherwarpExecutionSettings.rotateWaitServerTickEnabled)
        lifecycle.start(observer)
        hopField = field
        index = 1
        predictedPosition = path.first().eye
        if (announce) modMessage("\u00A7aExecuting ${path.size - 1} ${kind.name.lowercase()} teleports")
        return true
    }

    fun cancel() {
        rotationLease?.let(ServerRotationLeaseManager::release)
        rotationLease = null
        ServerRotationLeaseManager.releaseOwner(ServerRotationLeaseManager.PATHFINDER_OWNER)
        lifecycle.terminate(EtherwarpExecutionTermination.CANCELLED, deferObserver = true)
        planningSneak = false
        queuedUse?.let(PacketOrderManager::cancel)
        queuedUse = null
        rapidBurst.reset()
        executionCamera.clear()
        nodes = emptyList()
        index = 0
        finishing = false
        inFlight.clear()
        tickCounter = 0
        lastPos = null
        rapidProgress = EtherwarpRapidProgress(0, 0)
        aimWaitTicks = 0
        clipboardSet = false
        reanchors = 0
        predictedPosition = null
        pathGeneration++
        hopField = null
        executionSnapshot = null
        executionMode = EtherwarpExecutionMode.AWAIT_TELEPORT
        executionKeepLastServerRotation = false
        executionVariance.clear()
        authoritative.clear()
        rotateWaitSequence = null
        lifecycle.resetForReuse()
        lifecycle.flushDeferredTermination()
    }

    fun tick() {
        if (nodes.isEmpty()) return
        val player = mc.player ?: return
        val hadPendingLanding = inFlight.isNotEmpty()
        landingObserver.observe(player, executionMode != EtherwarpExecutionMode.AWAIT_TELEPORT, rapidProgress.furthestIndex, rapidProgress.progressTick)
        if (nodes.isEmpty()) return
        refreshHopField()
        if (nodes.isEmpty()) return
        if (shouldYieldAfterLandingObservation(executionMode, hadPendingLanding, finishing)) return
        if (shouldAwaitLanding(executionMode, inFlight.size)) return
        if (finishing) {
            if (inFlight.isEmpty()) finish()
            return
        }
        if (executionMode != EtherwarpExecutionMode.AWAIT_TELEPORT) {
            if (!EtherwarpServerTickGate.ready(rotateWaitSequence)) return
            return rapidBurst.tick(player)
        }
        val holding = isHoldingSkyblockItem("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END")
        val sneakReady = player.lastSentInput?.shift() == kind.sneak && (!kind.sneak || player.isCrouching)
        val abilityReady = SwapManager.canUseAbility
        if (holding && sneakReady && !abilityReady) return
        val targetLoaded = nodes.getOrNull(index)?.let { player.level().isLoaded(it.pos) } == true
        if (holding && sneakReady && !targetLoaded && ++aimWaitTicks > AIM_READY_TIMEOUT_TICKS) abort("Next node chunk did not load")
        if (holding && sneakReady && !targetLoaded) return
        val aim = if (holding && sneakReady && targetLoaded) liveAim() else null
        when (decide(holding, !holding && findEtherwarpableHotbarSlot() >= 0, sneakReady, aim != null, abilityReady)) {
            Decision.SWAP -> SwapManager.swapToEtherwarpableItem()
            Decision.CAST -> aim?.let {
                aimWaitTicks = 0
                cast(player, it)
            }
            Decision.CANCEL -> handleAimUnavailable(holding, sneakReady)
            Decision.WAIT -> Unit
        }
    }

    internal fun shouldAwaitLanding(mode: EtherwarpExecutionMode, pendingHops: Int): Boolean = mode == EtherwarpExecutionMode.AWAIT_TELEPORT && pendingHops > 0

    internal fun shouldYieldAfterLandingObservation(
        mode: EtherwarpExecutionMode,
        hadPendingLanding: Boolean,
        finishing: Boolean = false
    ): Boolean = mode == EtherwarpExecutionMode.AWAIT_TELEPORT && hadPendingLanding && !finishing

    fun shouldCaptureTeleportHistory(): Boolean = EtherwarpExecutionSettings.teleportSmoothingEnabled && nodes.isNotEmpty()

    fun onAuthoritativeTeleport(before: Vec3, actual: Vec3, nowNanos: Long = System.nanoTime()) = authoritative.observe(before, actual, nowNanos)

    fun smoothedRenderPosition(actual: Vec3, nowNanos: Long = System.nanoTime()): Vec3? = authoritative.position(actual, nowNanos)

    private fun handleAimUnavailable(holding: Boolean, sneakReady: Boolean) {
        if (!holding) return abort("No Aspect of the Void/End found")
        if (sneakReady && ++aimWaitTicks > AIM_READY_TIMEOUT_TICKS) abort("Next node did not become aimable live")
    }

    private fun cast(player: LocalPlayer, aim: Aim) {
        val generation = pathGeneration
        rotationLease = ServerRotationLeaseManager.request(
                ServerRotationLeaseManager.PATHFINDER_OWNER,
                aim.yaw,
                aim.pitch,
                ROTATION_LEASE_TICKS,
                ServerRotationLeaseManager.PATHFINDER_PRIORITY
            ) ?: return
        executionCamera.remember(executionMode, aim)
        queuedUse = PacketOrderManager.queueUseItem(aim.yaw, aim.pitch) { generation == pathGeneration && nodes.isNotEmpty() }
        val target = nodes[index]
        val origin = (if (inFlight.isEmpty()) mc.player?.let { Vec3(it.x, it.y, it.z) } else null) ?: predictedPosition ?: nodes[index - 1].eye
        inFlight.addLast(EtherwarpExecutionHop(index, target.eye, target.pos, aim, origin, tickCounter, player.lastSentInput?.shift() == true, player.isCrouching, player.onGround()))
        predictedPosition = target.eye
        index++
        if (index !in nodes.indices) finishing = true
    }

    private fun reanchor(from: Vec3): Boolean {
        val result = EtherwarpExecutionReanchor.resolve(kind, reanchors, MAX_REANCHORS, from, hopField, nodes) ?: return false
        inFlight.clear()
        rapidBurst.reset()
        reanchors++
        nodes = result.nodes
        index = result.index
        predictedPosition = result.predictedPosition
        finishing = false
        return true
    }

    private fun refreshHopField() { hopField = hopField?.let(EtherwarpHopField::refresh) }

    private fun onRapidCast(castIndex: Int, target: EtherwarpNode, aim: Aim, player: LocalPlayer) {
        executionCamera.remember(executionMode, aim)
        val origin = (if (inFlight.isEmpty()) mc.player?.let { Vec3(it.x, it.y, it.z) } else null) ?: predictedPosition ?: nodes[castIndex].eye
        inFlight.addLast(EtherwarpExecutionHop(castIndex + 1, target.eye, target.pos, aim, origin, tickCounter, player.lastSentInput?.shift() == true, player.isCrouching, player.onGround()))
        predictedPosition = target.eye
        index = castIndex + 2
    }

    private fun liveAim(): Aim? = EtherwarpExecutionAim.resolve(nodes, index, kind, predictedPosition, executionVariance, inFlight.isEmpty())

    private fun logLanding(hop: EtherwarpExecutionHop, landing: Vec3): Boolean = if (EtherwarpExecutionReporter.isExpected(landing, hop)) {
        EtherwarpExecutionReporter.logLanding(hop, landing)
    } else {
        logMiss(hop, landing)
        false
    }

    private fun logMiss(hop: EtherwarpExecutionHop, landing: Vec3) { clipboardSet = EtherwarpExecutionReporter.logMiss(hop, landing, kind, clipboardSet) }

    private fun finish() {
        executionCamera.applyFinal(executionMode, executionKeepLastServerRotation)
        lifecycle.terminate(EtherwarpExecutionTermination.ARRIVED, deferObserver = true)
        modMessage("\u00A7aArrived")
        cancel()
    }

    private fun abort(reason: String) {
        modMessage("\u00A7c$reason, stopping")
        lifecycle.terminate(EtherwarpExecutionTermination.FAILED, deferObserver = true)
        cancel()
    }
}
