package gobby.utils.rotation

import kotlin.math.abs
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.entity.Relative
import kotlin.math.round

object ServerRotationLeaseManager {
    const val PATHFINDER_OWNER = "gobby:pathfinder"
    const val PATHFINDER_PRIORITY = 100
    const val MAX_LEASE_TICKS = 200
    private const val FULL_TURN = 360.0
    private const val MAX_PITCH = 90f
    private const val MIN_PITCH = -90f
    private var nextLeaseId = 0L
    private var activeRequest: Request? = null
    private var previousServerRotation: Rotation? = null
    private var serverRotation: Rotation? = null
    private var observedLevel: Any? = null

    @Synchronized
    fun request(owner: String, yaw: Float, pitch: Float, ticks: Int, priority: Int): Lease? {
        require(owner.isNotBlank())
        require(yaw.isFinite() && pitch.isFinite())
        require(ticks in 1..MAX_LEASE_TICKS)
        val current = activeRequest
        if (current != null && priority < current.priority) return null
        val referenceYaw = current?.rotation?.yaw ?: serverRotation?.yaw ?: 0f
        val rotation = Rotation(nearestEquivalentYaw(yaw, referenceYaw), pitch.coerceIn(MIN_PITCH, MAX_PITCH))
        val id = ++nextLeaseId
        activeRequest = Request(id, owner, rotation, ticks, priority)
        return Lease(owner, id)
    }

    @Synchronized
    fun beginMovementTick(): MovementRotation? = activeRequest?.let { MovementRotation(it.id, it.rotation.yaw, it.rotation.pitch) }

    @Synchronized
    fun finishMovementTick(captured: MovementRotation?) {
        if (captured == null || activeRequest?.id != captured.leaseId) return
        val current = activeRequest ?: return
        activeRequest = current.copy(remainingTicks = current.remainingTicks - 1).takeUnless { it.remainingTicks <= 0 }
    }

    @Synchronized
    fun release(lease: Lease?): Boolean {
        if (lease == null) return false
        val current = activeRequest ?: return false
        if (current.id != lease.id || current.owner != lease.owner) return false
        activeRequest = null
        return true
    }

    @Synchronized
    fun isActive(lease: Lease?): Boolean {
        if (lease == null) return false
        val current = activeRequest ?: return false
        return current.id == lease.id && current.owner == lease.owner
    }

    @Synchronized
    fun releaseOwner(owner: String) {
        if (activeRequest?.owner == owner) activeRequest = null
    }

    @Synchronized
    fun reset() {
        clearState()
        observedLevel = null
    }

    @Synchronized
    fun observeClientState(level: Any?, connected: Boolean) {
        if (!connected || level == null) {
            reset()
            return
        }
        if (observedLevel != null && observedLevel !== level) clearState()
        observedLevel = level
    }

    @Synchronized
    fun activeRotation(): Rotation? = activeRequest?.rotation

    @Synchronized
    fun beginClientTick() {
        previousServerRotation = serverRotation
    }

    @Synchronized
    fun interpolatedRotation(progress: Float): Rotation? {
        val current = serverRotation ?: return null
        val previous = previousServerRotation ?: current
        val clamped = progress.coerceIn(0f, 1f)
        return Rotation(
            nearestEquivalentYaw(current.yaw, previous.yaw) * clamped + previous.yaw * (1f - clamped),
            current.pitch * clamped + previous.pitch * (1f - clamped)
        )
    }

    @Synchronized
    fun matchesObserved(yaw: Float, pitch: Float): Boolean {
        val current = serverRotation ?: return false
        return abs(nearestEquivalentYaw(current.yaw, yaw) - yaw) <= ROTATION_EPSILON &&
            abs(current.pitch - pitch) <= ROTATION_EPSILON
    }

    @Synchronized
    fun observeAcceptedOutgoing(packet: Packet<*>) {
        val fallback = serverRotation
        val observed = when (packet) {
            is ServerboundMovePlayerPacket -> if (packet.hasRotation()) Rotation(packet.getYRot(fallback?.yaw ?: 0f), packet.getXRot(fallback?.pitch ?: 0f)) else null
            is ServerboundUseItemPacket -> Rotation(packet.yRot, packet.xRot)
            else -> null
        }
        if (observed != null) setObservedRotation(observed.normalized())
    }

    @Synchronized
    fun rewriteOutgoingInteraction(packet: Packet<*>): Packet<*> {
        val current = activeRequest ?: return packet
        if (packet !is ServerboundUseItemPacket) return packet
        return ServerboundUseItemPacket(packet.hand, packet.sequence, current.rotation.yaw, current.rotation.pitch)
    }

    @Synchronized
    fun observeIncoming(packet: Packet<*>) {
        val current = serverRotation
        when (packet) {
            is ClientboundPlayerRotationPacket -> {
                if (current == null && (packet.relativeY() || packet.relativeX())) return
                setObservedRotation(Rotation(packet.yRot() + if (packet.relativeY()) current?.yaw ?: 0f else 0f, packet.xRot() + if (packet.relativeX()) current?.pitch ?: 0f else 0f).normalized())
            }
            is ClientboundPlayerPositionPacket -> {
                val relativeYaw = packet.relatives().contains(Relative.Y_ROT)
                val relativePitch = packet.relatives().contains(Relative.X_ROT)
                if (current == null && (relativeYaw || relativePitch)) return
                setObservedRotation(Rotation(packet.change().yRot() + if (relativeYaw) current?.yaw ?: 0f else 0f, packet.change().xRot() + if (relativePitch) current?.pitch ?: 0f else 0f).normalized())
            }
        }
    }

    fun nearestEquivalentYaw(targetYaw: Float, referenceYaw: Float): Float {
        require(targetYaw.isFinite() && referenceYaw.isFinite())
        val turns = round((referenceYaw - targetYaw) / FULL_TURN)
        val result = targetYaw + (turns * FULL_TURN).toFloat()
        require(abs(result.toDouble()) <= Float.MAX_VALUE)
        return result
    }

    data class Lease(val owner: String, val id: Long)
    data class MovementRotation(val leaseId: Long, val yaw: Float, val pitch: Float)
    data class Rotation(val yaw: Float, val pitch: Float)
    private data class Request(val id: Long, val owner: String, val rotation: Rotation, val remainingTicks: Int, val priority: Int)

    private fun Rotation.normalized() = copy(pitch = pitch.coerceIn(MIN_PITCH, MAX_PITCH))

    private fun setObservedRotation(rotation: Rotation) {
        previousServerRotation = serverRotation ?: rotation
        serverRotation = rotation
    }

    private fun clearState() {
        activeRequest = null
        previousServerRotation = null
        serverRotation = null
    }
    private const val ROTATION_EPSILON = 0.0001f
}
