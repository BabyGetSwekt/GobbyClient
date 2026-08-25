package gobby.pathfinder.etherwarp

import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3

internal class EtherwarpExecutionLandingObserver(
    private val route: () -> List<EtherwarpNode>,
    private val kind: () -> EtherwarpKind,
    private val inFlight: ArrayDeque<EtherwarpExecutionHop>,
    private val lastPosition: () -> Vec3?,
    private val updateLastPosition: (Vec3) -> Unit,
    private val nextTick: () -> Int,
    private val reanchor: (Vec3) -> Boolean,
    private val cancel: () -> Unit,
    private val logLanding: (EtherwarpExecutionHop, Vec3) -> Boolean,
    private val logMiss: (EtherwarpExecutionHop, Vec3) -> Unit,
    private val onRapidProgress: (Int, Int) -> Unit,
    private val onProgress: (Int) -> Unit,
    private val shouldRecoverPartial: (Int, Int, Int) -> Boolean,
    private val onPartialRecovery: () -> Unit
) {
    fun observe(player: LocalPlayer, rapidMode: Boolean, progressIndex: Int, progressTick: Int) {
        val tick = nextTick()
        val current = Vec3(player.x, player.y, player.z)
        val previous = lastPosition()
        updateLastPosition(current)
        if (inFlight.isEmpty()) {
            checkPartialRecovery(rapidMode, progressIndex, progressTick, tick)
            return
        }
        previous?.let {
            val movedSq = current.subtract(it).lengthSqr()
            if (movedSq > TELEPORT_MIN_SQ) observeMovement(current, movedSq, rapidMode, tick)
        }
        observeTimeout(current, tick)
        checkPartialRecovery(rapidMode, progressIndex, progressTick, tick)
    }

    private fun observeMovement(current: Vec3, movedSq: Double, rapidMode: Boolean, tick: Int) {
        val matched = furthestMatchingHopIndex(current, inFlight)
        if (matched != null) {
            repeat(matched) { inFlight.removeFirst() }
            val landed = inFlight.removeFirst()
            if (!logLanding(landed, current) && !reanchor(current)) return cancel()
            if (rapidMode) onRapidProgress(landed.label, tick)
            onProgress(landed.label)
        } else if (movedSq >= MISS_MIN_SQ) {
            logMiss(inFlight.first(), current)
            if (!reanchor(current)) cancel()
        }
    }

    private fun observeTimeout(current: Vec3, tick: Int) {
        if (inFlight.isEmpty() || tick - inFlight.first().firedTick <= LAND_TIMEOUT_TICKS) return
        inFlight.removeFirst().let { EtherwarpExecutionReporter.logDrop(it, kind()) }
        if (!reanchor(current)) cancel()
    }

    private fun checkPartialRecovery(rapidMode: Boolean, progressIndex: Int, progressTick: Int, tick: Int) {
        if (rapidMode && inFlight.isNotEmpty() && shouldRecoverPartial(progressIndex, route().lastIndex, tick - progressTick)) {
            onPartialRecovery()
        }
    }
}
