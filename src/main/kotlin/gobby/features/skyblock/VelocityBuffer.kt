package gobby.features.skyblock

import gobby.Gobbyclient.Companion.mc
import gobby.events.ClientTickEvent
import gobby.events.KeyPressGuiEvent
import gobby.events.PacketReceivedEvent
import gobby.events.WorldLoadEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.Category
import gobby.gui.click.KeybindSetting
import gobby.gui.click.Module
import gobby.gui.hud.HudSetting
import gobby.utils.ChatUtils.modMessage
import gobby.utils.render.NotificationRenderer
import gobby.utils.timer.Clock
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object VelocityBuffer : Module("Velocity Buffer", "Inbound lag switch for Bonzo Staff", Category.SKYBLOCK) {

    val toggleKey by KeybindSetting("Toggle", desc = "Keybind to toggle velocity buffer")

    private const val MAX_DURATION_MS = 9900L

    @Volatile private var active = false
    private val releasing = AtomicBoolean(false)
    private val queue = ConcurrentLinkedQueue<Packet<*>>()
    private val velocityCount = AtomicInteger(0)
    private val clock = Clock()
    private var attackKeyLastTick = false

    private val bufferHud by HudSetting("Velocity Buffer", "Shows captured velocity count",
        visible = { active }
    ) { example ->
        val ctx = drawContext ?: return@HudSetting
        val vc = if (example) 2 else velocityCount.get()
        val tc = if (example) 47 else queue.size
        val text = "§bVelo: §f$vc §8| §7Total: §f$tc"
        val tr = mc.font
        ctx.text(tr, text, 0, 0, Color.WHITE.rgb, true)
        setSize(tr.width(text), tr.lineHeight)
    }

    @SubscribeEvent
    fun onKeyPress(event: KeyPressGuiEvent) {
        if (!enabled || mc.gui.screen() != null) return
        if (toggleKey == 0 || event.key != toggleKey) return
        if (active) stop() else start()
    }

    private fun start() {
        active = true
        clock.update()
        queue.clear()
        velocityCount.set(0)
        modMessage("§aVelocity Buffer ON")
        NotificationRenderer.show("Velocity Buffer", true)
    }

    private fun stop() {
        active = false
        flushAll()
        NotificationRenderer.show("Velocity Buffer", false)
    }

    @Suppress("UNCHECKED_CAST")
    private fun flushAll() {
        if (!releasing.compareAndSet(false, true)) return
        val count = queue.size
        val handler = mc.connection
        if (handler != null) {
            mc.execute {
                while (queue.isNotEmpty()) {
                    val packet = queue.poll() ?: break
                    try { (packet as Packet<ClientGamePacketListener>).handle(handler) } catch (_: Exception) {}
                }
                velocityCount.set(0)
                releasing.set(false)
                if (count > 0) modMessage("§eFlushed $count packets")
            }
        } else {
            queue.clear()
            velocityCount.set(0)
            releasing.set(false)
        }
    }

    @SubscribeEvent
    fun onTick(event: ClientTickEvent.Post) {
        if (!enabled || !active) { attackKeyLastTick = false; return }

        if (clock.hasTimePassed(MAX_DURATION_MS)) {
            stop()
            modMessage("§cVelocity buffer auto-disabled (timeout).")
            attackKeyLastTick = false
            return
        }

        val attackPressed = mc.options.keyAttack.isDown
        if (attackPressed && !attackKeyLastTick && velocityCount.get() > 0) {
            releaseOneVelocity()
        }
        attackKeyLastTick = attackPressed
    }

    @SubscribeEvent
    fun onPacketReceived(event: PacketReceivedEvent) {
        if (!enabled || !active || releasing.get()) return

        val packet = event.packet

        if (packet is ClientboundKeepAlivePacket || packet is ClientboundDisconnectPacket) return

        event.cancel()

        if (packet is ClientboundSetEntityMotionPacket && packet.id == (mc.player?.id ?: -1)) {
            val vel = packet.movement
            modMessage("§d[Velocity captured] §f(${f(vel.x)}, ${f(vel.y)}, ${f(vel.z)})")
            velocityCount.incrementAndGet()
        }

        queue.add(packet)
    }

    @Suppress("UNCHECKED_CAST")
    private fun releaseOneVelocity() {
        if (!releasing.compareAndSet(false, true)) return

        val handler = mc.connection
        if (handler == null) { releasing.set(false); return }

        mc.execute {
            val playerId = mc.player?.id ?: -1
            val remaining = mutableListOf<Packet<*>>()
            var found = false

            while (queue.isNotEmpty()) {
                val packet = queue.poll() ?: break
                if (!found) {
                    try { (packet as Packet<ClientGamePacketListener>).handle(handler) } catch (_: Exception) {}
                    if (packet is ClientboundSetEntityMotionPacket && packet.id == playerId) {
                        val vel = packet.movement
                        modMessage("§aReleased velocity (${f(vel.x)}, ${f(vel.y)}, ${f(vel.z)}) §7[${velocityCount.get() - 1} left]")
                        velocityCount.decrementAndGet()
                        found = true
                    }
                } else {
                    remaining.add(packet)
                }
            }

            queue.addAll(remaining)
            releasing.set(false)

            if (velocityCount.get() <= 0) stop()
        }
    }

    @SubscribeEvent
    fun onWorldUnload(event: WorldLoadEvent) {
        active = false
        queue.clear()
        velocityCount.set(0)
    }

    private fun f(d: Double): String = "%.2f".format(d)
}
