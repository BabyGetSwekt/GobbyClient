package gobby.pathfinder.etherwarp

import java.util.concurrent.atomic.AtomicLong

internal object EtherwarpServerTickGate {
    private val sequence = AtomicLong()

    fun arm(mode: EtherwarpExecutionMode, enabled: Boolean = true): Long? = mode.takeIf {
        enabled && (it == EtherwarpExecutionMode.ROTATE || it == EtherwarpExecutionMode.SERVER_ROTATE)
    }?.let { sequence.get() }

    fun ready(armedSequence: Long?): Boolean = armedSequence == null || sequence.get() > armedSequence

    fun onServerTickPing(packetId: Int) {
        if (packetId == 0) return
        sequence.incrementAndGet()
    }
}
