package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.PacketReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.ChatUtils.modMessage
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object SoundDebugger : Module("Sound Debugger", "Prints every sound played within range", Category.DEVELOPER) {

    private val range by NumberSetting("Range", default = 32, min = 1, max = 64, step = 1, desc = "Block radius around player")
    private val detectFireworks by BooleanSetting("Detect Fireworks", true, desc = "Also log firework rocket explosions (client-generated via EntityStatus 17)")

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (!enabled) return
        val player = mc.player ?: return
        when (val packet = event.packet) {
            is ClientboundSoundPacket -> reportPositionedSound(packet, player)
            is ClientboundSoundEntityPacket -> reportEntitySound(packet, player)
            is ClientboundEntityEventPacket -> reportFireworkBlast(packet, player)
        }
    }

    private fun outOfRange(x: Double, y: Double, z: Double, player: net.minecraft.world.entity.player.Player): Boolean {
        val dx = x - player.x
        val dy = y - player.y
        val dz = z - player.z
        return sqrt(dx * dx + dy * dy + dz * dz) > range
    }

    private fun reportPositionedSound(packet: ClientboundSoundPacket, player: net.minecraft.world.entity.player.Player) {
        if (outOfRange(packet.x, packet.y, packet.z, player)) return
        val id = packet.sound.value().location().toString()
        modMessage("§7[Sound] §f$id §8| §bpos §f(${format(packet.x)}, ${format(packet.y)}, ${format(packet.z)}) §8| §dpitch §f${format(packet.pitch.toDouble())}")
    }

    private fun reportEntitySound(packet: ClientboundSoundEntityPacket, player: net.minecraft.world.entity.player.Player) {
        val entity = mc.level?.getEntity(packet.id) ?: return
        if (outOfRange(entity.x, entity.y, entity.z, player)) return
        val id = packet.sound.value().location().toString()
        modMessage("§7[Sound] §f$id §8| §bpos §f(${format(entity.x)}, ${format(entity.y)}, ${format(entity.z)}) §8| §dpitch §f${format(packet.pitch.toDouble())} §8| §7entity#${packet.id}")
    }

    private fun reportFireworkBlast(packet: ClientboundEntityEventPacket, player: net.minecraft.world.entity.player.Player) {
        if (!detectFireworks || packet.eventId.toInt() != FIREWORK_BLAST_EVENT) return
        val world = mc.level ?: return
        val live = packet.getEntity(world)
        val pos = if (live != null) Vec3(live.x, live.y, live.z) else ParticleDebugger.fireworkPos(packet) ?: return
        if (outOfRange(pos.x, pos.y, pos.z, player)) return
        modMessage("§7[Sound] §6entity.firework_rocket.blast §8| §bpos §f(${format(pos.x)}, ${format(pos.y)}, ${format(pos.z)})")
    }

    private fun format(value: Double): String = "%.2f".format(value)

    private const val FIREWORK_BLAST_EVENT = 17
}
