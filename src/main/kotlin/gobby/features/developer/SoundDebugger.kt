package gobby.features.developer

import gobby.Gobbyclient.Companion.mc
import gobby.events.PacketReceivedEvent
import gobby.events.core.SubscribeEvent
import gobby.gui.click.BooleanSetting
import gobby.gui.click.Category
import gobby.gui.click.Module
import gobby.gui.click.NumberSetting
import gobby.utils.ChatUtils.modMessage
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import kotlin.math.sqrt

object SoundDebugger : Module("Sound Debugger", "Prints every sound played within range", Category.DEVELOPER) {

    private val range by NumberSetting("Range", default = 32, min = 1, max = 64, step = 1, desc = "Block radius around player")
    private val detectFireworks by BooleanSetting("Detect Fireworks", true, desc = "Also log firework rocket explosions (client-generated via EntityStatus 17)")

    @SubscribeEvent
    fun onPacket(event: PacketReceivedEvent) {
        if (!enabled) return
        val player = mc.player ?: return
        when (val packet = event.packet) {
            is PlaySoundS2CPacket -> {
                val dx = packet.x - player.x; val dy = packet.y - player.y; val dz = packet.z - player.z
                if (sqrt(dx * dx + dy * dy + dz * dz) > range) return
                val id = packet.sound.idAsString
                val pitch = "%.2f".format(packet.pitch)
                val x = "%.2f".format(packet.x); val y = "%.2f".format(packet.y); val z = "%.2f".format(packet.z)
                modMessage("§7[Sound] §f$id §8| §bpos §f($x, $y, $z) §8| §dpitch §f$pitch")
            }
            is PlaySoundFromEntityS2CPacket -> {
                val entity = mc.world?.getEntityById(packet.entityId)
                val ex = entity?.x ?: return
                val ey = entity.y; val ez = entity.z
                val dx = ex - player.x; val dy = ey - player.y; val dz = ez - player.z
                if (sqrt(dx * dx + dy * dy + dz * dz) > range) return
                val id = packet.sound.idAsString
                val pitch = "%.2f".format(packet.pitch)
                modMessage("§7[Sound] §f$id §8| §bpos §f(${"%.2f".format(ex)}, ${"%.2f".format(ey)}, ${"%.2f".format(ez)}) §8| §dpitch §f$pitch §8| §7entity#${packet.entityId}")
            }
            is EntityStatusS2CPacket -> {
                if (!detectFireworks) return
                if (packet.status.toInt() != 17) return
                val world = mc.world ?: return
                val live = packet.getEntity(world)
                val pos = if (live != null) net.minecraft.util.math.Vec3d(live.x, live.y, live.z) else ParticleDebugger.fireworkPos(packet) ?: return
                val dx = pos.x - player.x; val dy = pos.y - player.y; val dz = pos.z - player.z
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                if (dist > range) return
                modMessage("§7[Sound] §6entity.firework_rocket.blast §8| §bpos §f(${"%.2f".format(pos.x)}, ${"%.2f".format(pos.y)}, ${"%.2f".format(pos.z)}) §8| §7dist §f${"%.2f".format(dist)}")
            }
        }
    }
}
