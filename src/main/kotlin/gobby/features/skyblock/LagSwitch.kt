package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.KeyPressGuiEvent
import gobby.events.PacketSentEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.utils.ChatUtils.modMessage
import gobby.utils.render.NotificationRenderer
import gobby.utils.timer.Clock
import net.minecraft.network.packet.Packet

object LagSwitch : Module("Lag Switch", "Freezes outgoing packets", Category.SKYBLOCK) {

    val toggleKey by KeybindSetting("Toggle Lag", desc = "Keybind to toggle lag switch")

    private const val MAX_DURATION_MS = 9900L
    private const val MAX_QUEUE = 5000

    private var choking = false
    private var flushing = false
    private val queue = mutableListOf<Packet<*>>()
    private val clock = Clock()

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled || mc.currentScreen != null) return
        if (toggleKey == 0 || event.key != toggleKey) return
        if (choking) stop() else start()
    }

    private fun start() {
        choking = true
        clock.update()
        queue.clear()
        NotificationRenderer.show("Lag Switch", true)
    }

    private fun stop() {
        choking = false
        flush()
        NotificationRenderer.show("Lag Switch", false)
    }

    private fun flush() {
        if (queue.isEmpty()) return
        flushing = true
        val handler = mc.networkHandler
        queue.forEach { handler?.sendPacket(it) }
        queue.clear()
        flushing = false
    }

    @SubscribeEvent
    fun onPacketSent(event: PacketSentEvent) {
        if (!enabled || !choking || flushing) return

        if (clock.hasTimePassed(MAX_DURATION_MS)) {
            stop()
            modMessage("§cLag switch auto-disabled (timeout).")
            return
        }

        event.cancel()
        if (queue.size < MAX_QUEUE) {
            queue.add(event.packet)
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        choking = false
        queue.clear()
    }
}
