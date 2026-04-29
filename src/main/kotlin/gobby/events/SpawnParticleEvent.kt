package gobby.events

import net.minecraft.particle.ParticleEffect
import net.minecraft.util.math.Vec3d

class SpawnParticleEvent(
    val effect: ParticleEffect,
    val pos: Vec3d,
    val velocity: Vec3d
) : Events.Cancelable<Unit>()
