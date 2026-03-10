package gobby.mixin;

import gobby.features.skyblock.FreeCam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void gobbyclient$onCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!FreeCam.INSTANCE.getEnabled()) return;
        FreeCam.INSTANCE.updateMovement();
        setPos(FreeCam.INSTANCE.getCamX(), FreeCam.INSTANCE.getCamY(), FreeCam.INSTANCE.getCamZ());
        setRotation(FreeCam.INSTANCE.getCamYaw(), FreeCam.INSTANCE.getCamPitch());
    }

    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void gobbyclient$isThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (FreeCam.INSTANCE.getEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
