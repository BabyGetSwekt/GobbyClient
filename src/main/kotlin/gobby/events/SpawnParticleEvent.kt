package gobby.events

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.world.phys.Vec3

class SpawnParticleEvent(
    val effect: ParticleOptions,
    val pos: Vec3,
    val velocity: Vec3
) : Events.Cancelable<Unit>()
