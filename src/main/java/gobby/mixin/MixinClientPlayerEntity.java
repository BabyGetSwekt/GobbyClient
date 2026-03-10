package gobby.mixin;

import gobby.features.skyblock.FreeCam;
import gobby.mixin.accessor.LimbAnimatorAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class MixinClientPlayerEntity {

    @Inject(method = "tick", at = @At("RETURN"))
    private void gobbyclient$afterTick(CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;

        Vec3d vel = self.getVelocity();
        double horizontalSpeed = vel.x * vel.x + vel.z * vel.z;
        if (horizontalSpeed < 0.0001) {
            LimbAnimatorAccessor limb = (LimbAnimatorAccessor) self.limbAnimator;
            limb.setSpeed(0f);
            limb.setLastSpeed(0f);
        }
    }
}
