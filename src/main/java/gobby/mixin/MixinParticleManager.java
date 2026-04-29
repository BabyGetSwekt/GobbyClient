package gobby.mixin;

import gobby.Gobbyclient;
import gobby.events.SpawnParticleEvent;
import gobby.features.render.DisableBlockParticles;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class MixinParticleManager {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$onParticleSpawn(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        SpawnParticleEvent event = new SpawnParticleEvent(effect, new Vec3d(x, y, z), new Vec3d(vx, vy, vz));
        if (Gobbyclient.EVENT_MANAGER.publish(event).isCanceled()) cir.setReturnValue(null);
    }

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$disableBlockParticles(Particle particle, CallbackInfo ci) {
        if (DisableBlockParticles.INSTANCE.getEnabled() && particle instanceof BlockDustParticle) {
            ci.cancel();
        }
    }
}
