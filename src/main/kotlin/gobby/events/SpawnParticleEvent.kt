package gobby.events

import net.minecraft.network.packet.s2c.play.ParticleS2CPacket
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleType
import net.minecraft.util.math.Vec3d

class SpawnParticleEvent(
    val packet: ParticleS2CPacket,
    val type: ParticleType<out ParticleEffect>,
    val pos: Vec3d
) : Events.Cancelable<Unit>()