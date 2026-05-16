package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.SpawnParticleEvent;
import gobby.features.render.DisableBlockParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine {

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onParticleSpawn(ParticleOptions effect, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        SpawnParticleEvent event = new SpawnParticleEvent(effect, new Vec3(x, y, z), new Vec3(vx, vy, vz));
        if (Gobbyclient.EVENT_MANAGER.publish(event).isCanceled()) cir.setReturnValue(null);
    }

    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$disableBlockParticles(Particle particle, CallbackInfo ci) {
        if (DisableBlockParticles.INSTANCE.getEnabled() && particle instanceof TerrainParticle) {
            ci.cancel();
        }
    }
}
