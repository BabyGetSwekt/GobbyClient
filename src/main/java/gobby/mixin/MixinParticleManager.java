package gobby.mixin;

import gobby.config.GobbyConfig;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public class MixinParticleManager {

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$disableBlockParticles(Particle particle, CallbackInfo ci) {
        if (GobbyConfig.INSTANCE.getDisableBlockParticles() && particle instanceof BlockDustParticle) {
            ci.cancel();
        }
    }
}
